#!/usr/bin/env python3
"""Shared Jira REST helpers for seeding project LB. Import-only (no side effects)."""
import os, json, base64, urllib.request, urllib.error, time

def load_env(path):
    env = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line: continue
            k, v = line.split('=', 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env

_ENV = load_env(os.path.join(os.path.dirname(__file__), '.env'))
BASE = _ENV['JIRA_BASE_URL'].rstrip('/')
_AUTH = base64.b64encode(f"{_ENV['JIRA_EMAIL']}:{_ENV['JIRA_API_TOKEN']}".encode()).decode()

PROJECT = 'LB'
TEAM_FIELD = 'customfield_10001'
LABEL = 'seed-2026-06-28'
TEAM_IDS = {  # app team name -> Atlassian Team field id (set as RAW STRING on create)
    'Команда победителей': '6561d22e-9aca-4c4b-ab70-de65fdf5f33e',
    'Красивые': '214c5268-ece4-492b-9290-50f74e2e2f3d',
}
COMPONENT = {'name': 'Backend'}

def req(method, path, body=None, retries=3):
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(retries):
        r = urllib.request.Request(BASE + path, data=data, method=method)
        r.add_header('Authorization', 'Basic ' + _AUTH)
        r.add_header('Accept', 'application/json')
        if data: r.add_header('Content-Type', 'application/json')
        try:
            with urllib.request.urlopen(r) as resp:
                t = resp.read().decode(); return resp.status, (json.loads(t) if t else {})
        except urllib.error.HTTPError as e:
            body_txt = e.read().decode()
            if e.code in (429, 500, 502, 503) and attempt < retries - 1:
                time.sleep(1.5 * (attempt + 1)); continue
            return e.code, body_txt
    return 0, 'retries exhausted'

def adf(text):
    return {"type": "doc", "version": 1, "content": [
        {"type": "paragraph", "content": [{"type": "text", "text": text}]}]}

def create(issuetype, summary, description, team_name=None, parent=None,
           estimate=None, extra_labels=None):
    fields = {
        "project": {"key": PROJECT},
        "issuetype": {"name": issuetype},
        "summary": summary,
        "description": adf(description),
        "components": [COMPONENT],
        "labels": [LABEL] + (extra_labels or []),
    }
    if parent: fields["parent"] = {"key": parent}
    if team_name and team_name in TEAM_IDS:
        fields[TEAM_FIELD] = TEAM_IDS[team_name]  # Atlassian Team field: raw id string
    if estimate: fields["timetracking"] = {"originalEstimate": estimate}
    return req('POST', '/rest/api/3/issue', {"fields": fields})

def one_forward(key):
    """Apply a single forward (non-'назад') transition. Returns new status name or None."""
    s, d = req('GET', f'/rest/api/3/issue/{key}/transitions')
    if not isinstance(d, dict): return None
    fwd = next((t for t in d.get('transitions', []) if not t['name'].lower().startswith('назад')), None)
    if not fwd: return None
    req('POST', f'/rest/api/3/issue/{key}/transitions', {"transition": {"id": fwd['id']}})
    return fwd['to']['name']

def transition_to(key, target_status_names):
    """Walk available transitions toward one of target_status_names (best effort)."""
    for _ in range(4):
        s, d = req('GET', f'/rest/api/3/issue/{key}/transitions')
        if not isinstance(d, dict): return False, str(d)[:200]
        trs = d.get('transitions', [])
        # direct hit
        hit = next((t for t in trs if t['to']['name'] in target_status_names), None)
        if hit:
            req('POST', f'/rest/api/3/issue/{key}/transitions', {"transition": {"id": hit['id']}})
            return True, hit['to']['name']
        # otherwise step forward (avoid "Назад"/back transitions)
        fwd = next((t for t in trs if not t['name'].lower().startswith('назад')), None)
        if not fwd: return False, 'no forward transition'
        req('POST', f'/rest/api/3/issue/{key}/transitions', {"transition": {"id": fwd['id']}})
    return False, 'not reached'
