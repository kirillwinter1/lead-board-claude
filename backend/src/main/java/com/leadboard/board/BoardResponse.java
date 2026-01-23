package com.leadboard.board;

import java.util.List;

public class BoardResponse {

    private List<BoardNode> items;
    private int total;

    public BoardResponse() {
    }

    public BoardResponse(List<BoardNode> items, int total) {
        this.items = items;
        this.total = total;
    }

    public List<BoardNode> getItems() {
        return items;
    }

    public void setItems(List<BoardNode> items) {
        this.items = items;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
