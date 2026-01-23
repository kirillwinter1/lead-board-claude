package com.leadboard.controller;

import com.leadboard.board.BoardResponse;
import com.leadboard.board.BoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ResponseEntity<BoardResponse> getBoard() {
        BoardResponse response = boardService.getBoard();
        return ResponseEntity.ok(response);
    }
}
