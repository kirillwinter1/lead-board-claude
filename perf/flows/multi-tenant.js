// multi-tenant.js: Cross-tenant flow — cycle through all 3 tenants

import { TENANTS, TEAMS_PER_TENANT } from '../config/env.js';
import { boardFlow } from './board.js';
import { dataQualityFlow } from './data-quality.js';
import { sleep } from 'k6';
import { thinkTime } from '../lib/generators.js';

/**
 * Multi-tenant flow: Board + Data Quality for each of the 3 tenants.
 * Tests cross-tenant isolation and schema switching overhead.
 * @param {number} userIndex - 1-10
 */
export function multiTenantFlow(userIndex) {
    for (const tenant of TENANTS) {
        const teamIndex = (userIndex % TEAMS_PER_TENANT) + 1;

        boardFlow(tenant.slug, tenant.name, userIndex, teamIndex);
        sleep(thinkTime());

        dataQualityFlow(tenant.slug, tenant.name, userIndex, teamIndex);
        sleep(thinkTime());
    }
}
