package org.kiko.dev;

import java.util.List;

public class CompletedGameInfo {
    private String queueType;
    private String gameId;
    private boolean win;

    private List<CompletedGameInfoParticipant> participants;

    public CompletedGameInfo() {
        this.queueType = "";
        this.gameId = "";
        this.win = false;
        this.participants = null;
    }

    // Getters
    public String getQueueType() { return queueType; }
    public String getGameId() { return gameId; }
    public boolean getWin() { return win; }
    public List<CompletedGameInfoParticipant> getParticipants() { return participants; }


    //Setters
    public void setQueueType(String queueType) { this.queueType = queueType; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public void setWin(boolean win) { this.win = win; }
    public void setParticipants(List<CompletedGameInfoParticipant> participants) { this.participants = participants; }
}
