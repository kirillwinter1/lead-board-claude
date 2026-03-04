package com.leadboard.board;

import java.util.List;

public record BoardSearchResponse(List<String> matchedEpicKeys, String searchMode) {}
