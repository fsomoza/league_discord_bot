package org.kiko.dev;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class RiotApiAdapter {

    private static final String BASE_URL = "https://europe.api.riotgames.com";
    private static final String ACCOUNT_BASE_URL = "https://euw1.api.riotgames.com";
    private  final String RIOT_API_KEY;


    private final HttpClient client;
    private final Gson gson;

    // Private constructor to prevent instantiation
    private RiotApiAdapter() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
       RIOT_API_KEY = ConfigurationHolder.getProperty("riot.api.key");
    }

    // Holder class for lazy-loaded singleton instance
    private static class Holder {
        private static final RiotApiAdapter INSTANCE = new RiotApiAdapter();
    }

    // Public method to provide access to the singleton instance
    public static RiotApiAdapter getInstance() {
        return Holder.INSTANCE;
    }

    public AccountInfo getPuuid(String name, String tagLine) throws Exception {

        String encodedGameName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String encodedTagLine = URLEncoder.encode(tagLine, StandardCharsets.UTF_8);
        String endpoint = String.format("/riot/account/v1/accounts/by-riot-id/%s/%s",
                encodedGameName, encodedTagLine);

        //TODO VERY REPEATED CODE, MAYBE NEEDS TO BE ABSTRACTED?
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return gson.fromJson(response.body(), AccountInfo.class);
        } else {
            System.out.println("peta aqui");
            handleErrorResponse(response);
            return null; // Or throw an exception based on your error handling strategy
        }
    }

    public CurrentGameInfo checkIfPlayerIsInGame(String puuid, Map<String, String> playersMap) throws Exception {

        CurrentGameInfo currentGameInfo = new CurrentGameInfo();


        String endpoint = String.format("/lol/spectator/v5/active-games/by-summoner/%s",
                URLEncoder.encode(puuid, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
            // Ensure you retrieve the string value for comparison

                currentGameInfo.setGameId(jsonObject.get("gameId").getAsString());
                currentGameInfo.setQueueType(jsonObject.get("gameQueueConfigId").getAsString());
                List<Participant> participants = new ArrayList<>();
                currentGameInfo.setParticipants(participants);
                int counter = 0;
                for (var participantElement : jsonObject.get("participants").getAsJsonArray()) {
                    counter++;
                    System.out.println(counter);

                    JsonObject participant = participantElement.getAsJsonObject();
                    String participantPuuid = participant.get("puuid").getAsString();

                    if (playersMap.containsKey(participantPuuid)) {

                        Participant participantObj = new Participant();
                        participantObj.setPuuid(participantPuuid);
                        participantObj.setChampionId(participant.get("championId").getAsString());
                        participantObj.setPlayerName(playersMap.get(participantPuuid));
                        participants.add(participantObj);
                        currentGameInfo.setParticipants(participants);
                        playersMap.remove(participantPuuid);

                    }
                }
                    return currentGameInfo;
        } else {
            //handleErrorResponse(response);
            return null;
        }
    }

    public CompletedGameInfo checkCompletedGame(String gameId, Set<String> participantPuuids) throws Exception {

        //TODO: need to cover casuistic where players are in opposite teams

        CompletedGameInfo completedGameInfo = new CompletedGameInfo();
        List<CompletedGameInfoParticipant> completedGameInfoParticipants = new ArrayList<>();
        completedGameInfo.setParticipants(completedGameInfoParticipants);

        Boolean win = null;

        // Encode the gameId to ensure it's safe for use in a URL
        String encodedGameId = URLEncoder.encode(gameId, StandardCharsets.UTF_8);

        // Complete the endpoint by adding '/ids' and the query parameters
        String endpoint = String.format("/lol/match/v5/matches/%s", encodedGameId);


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
            JsonObject info =  jsonObject.get("info").getAsJsonObject();
            JsonArray participants = info.get("participants").getAsJsonArray();
            for (JsonElement participantElement : participants) {
                JsonObject participant = participantElement.getAsJsonObject();
                String participantPuuid = participant.get("puuid").getAsString();
                if (participantPuuids.contains(participantPuuid)) {

                   if (win == null){
                       win = participant.get("win").getAsBoolean();
                   }

                    JsonObject player = participant.getAsJsonObject();
                    CompletedGameInfoParticipant completedGameInfoParticipant = new CompletedGameInfoParticipant();
                    completedGameInfoParticipant.setPlayerName(player.get("riotIdGameName").getAsString());
                    completedGameInfoParticipant.setChampion(player.get("championName").getAsString());
                    //completedGameInfo.setWin(player.get("win").getAsBoolean());
                    String kills = player.get("kills").getAsString();
                    String deaths = player.get("deaths").getAsString();
                    String assists = player.get("assists").getAsString();
                    completedGameInfoParticipant.setKda(kills + "/" + deaths + "/" + assists);
                    completedGameInfoParticipants.add(completedGameInfoParticipant);
                }
            }
            completedGameInfo.setWin(win);
            return completedGameInfo;
        }else{
            //handleErrorResponse(response);
            System.out.println(response.body());
            return null;
        }

    }

    public String searchGameId(String puuid, String gameId) throws Exception {


        // Encode the PUUID to ensure it's safe for use in a URL
        String encodedPuuid = URLEncoder.encode(puuid, StandardCharsets.UTF_8);

        // Complete the endpoint by adding '/ids' and the query parameters
        String endpoint = String.format("/lol/match/v5/matches/by-puuid/%s/ids?start=0&count=30", encodedPuuid);

        // Build the full URI
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            // Parse the response body as a JSON array
            JsonArray matches = gson.fromJson(response.body(), JsonArray.class);

            // Iterate through each match ID in the array
            for (JsonElement matchElement : matches) {
                String matchId = matchElement.getAsString();
                if (matchId.equalsIgnoreCase("EUW1_"+gameId)) {
                    return matchId;
                }
            }
        } else {
            // Handle non-200 responses appropriately
            System.err.println("Failed to fetch matches. HTTP Status Code: " + response.statusCode());
            // Optionally, you can throw an exception or handle it as per your application's requirement
        }

        return null;
    }

    public String getEncryptedSummonerId(String puuid) throws Exception {
        String endpoint = String.format("/lol/summoner/v4/summoners/by-puuid/%s",
                URLEncoder.encode(puuid, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
            return jsonObject.get("id").getAsString();
        } else {
            handleErrorResponse(response);
            return null;
        }
    }

    public String getSoloQueueRank(String encryptedSummonerId) throws Exception {
        String endpoint = String.format("/lol/league/v4/entries/by-summoner/%s",
                URLEncoder.encode(encryptedSummonerId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            Type listType = new TypeToken<List<LeagueEntry>>() {}.getType();
            List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

            Optional<LeagueEntry> soloQueue = entries.stream()
                    .filter(entry -> "RANKED_SOLO_5x5".equals(entry.getQueueType()))
                    .findFirst();

            if (soloQueue.isPresent()) {
                LeagueEntry entry = soloQueue.get();
                return String.format("%s %s %d LP",
                        entry.getTier(),
                        entry.getRank(),
                        entry.getLeaguePoints());
            } else {
                return "JUEGA RANKEDS";
            }
        } else {
            handleErrorResponse(response);
            return null;
        }
    }

    public String getFlexQueueRank(String encryptedSummonerId) throws Exception {
        String endpoint = String.format("/lol/league/v4/entries/by-summoner/%s",
                URLEncoder.encode(encryptedSummonerId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            Type listType = new TypeToken<List<LeagueEntry>>() {}.getType();
            List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

            Optional<LeagueEntry> flexQueue = entries.stream()
                    .filter(entry -> "RANKED_FLEX_SR".equals(entry.getQueueType()))
                    .findFirst();

            if (flexQueue.isPresent()) {
                LeagueEntry entry = flexQueue.get();
                return String.format("%s %s %d LP",
                        entry.getTier(),
                        entry.getRank(),
                        entry.getLeaguePoints());
            } else {
                return "JUEGA RANKEDS";
            }
        } else {
            handleErrorResponse(response);
            return null;
        }
    }


    public Map<String, String> getQueueRanks(String encryptedSummonerId) throws Exception {
        String endpoint = String.format("/lol/league/v4/entries/by-summoner/%s",
                URLEncoder.encode(encryptedSummonerId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        Map<String, String> ranks = new HashMap<>();
        ranks.put("RANKED_SOLO_5x5", "JUEGA RANKEDS");
        ranks.put("RANKED_FLEX_SR", "JUEGA RANKEDS");

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            Type listType = new TypeToken<List<LeagueEntry>>() {}.getType();
            List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

            entries.stream()
                    .filter(entry -> entry.getQueueType().equals("RANKED_SOLO_5x5") ||
                            entry.getQueueType().equals("RANKED_FLEX_SR"))
                    .forEach(entry -> ranks.put(
                            entry.getQueueType(),
                            String.format("%s %s %d LP",
                                    entry.getTier(),
                                    entry.getRank(),
                                    entry.getLeaguePoints())
                    ));

            return ranks;
        } else {
            handleErrorResponse(response);
            return null;
        }
    }


    public LeagueEntry getSoloQueusdsdseRank(String encryptedSummonerId) throws Exception {
        String endpoint = String.format("/lol/league/v4/entries/by-summoner/%s",
                URLEncoder.encode(encryptedSummonerId, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_BASE_URL + endpoint))
                .header("X-Riot-Token", RIOT_API_KEY)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            Type listType = new TypeToken<List<LeagueEntry>>() {}.getType();
            List<LeagueEntry> entries = gson.fromJson(response.body(), listType);

            Optional<LeagueEntry> soloQueue = entries.stream()
                    .filter(entry -> "RANKED_SOLO_5x5".equals(entry.getQueueType()))
                    .findFirst();

            if (soloQueue.isPresent()) {
                LeagueEntry entry = soloQueue.get();
                return entry;
            } else {
                return null;
            }
        } else {
            handleErrorResponse(response);
            return null;
        }
    }

    // Helper method to handle non-200 responses
    private void handleErrorResponse(HttpResponse<String> response) throws Exception {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        switch (statusCode) {
            case 401:
                throw new Exception("Unauthorized: Check your API key.");
            case 404:
                throw new Exception("Not Found: The requested resource could not be found.");
            case 429:
                throw new Exception("Rate Limited: You have exceeded the API rate limit.");
            default:
                throw new Exception("Error: Received status code " + statusCode + " with message: " + responseBody);
        }
    }

    // Static nested class for deserializing league entries
    public static class LeagueEntry {
        private String leagueId;
        private String summonerId;
        private String queueType;
        private String tier;
        private String rank;
        private int leaguePoints;
        private int wins;
        private int losses;
        private boolean hotStreak;
        private boolean veteran;
        private boolean freshBlood;
        private boolean inactive;

        // Getters
        public String getLeagueId() { return leagueId; }
        public String getSummonerId() { return summonerId; }
        public String getQueueType() { return queueType; }
        public String getTier() { return tier; }
        public String getRank() { return rank; }
        public int getLeaguePoints() { return leaguePoints; }
        public int getWins() { return wins; }
        public int getLosses() { return losses; }
        public boolean isHotStreak() { return hotStreak; }
        public boolean isVeteran() { return veteran; }
        public boolean isFreshBlood() { return freshBlood; }
        public boolean isInactive() { return inactive; }

        // Setters (if needed)
        public void setLeagueId(String leagueId) { this.leagueId = leagueId; }
        public void setSummonerId(String summonerId) { this.summonerId = summonerId; }
        public void setQueueType(String queueType) { this.queueType = queueType; }
        public void setTier(String tier) { this.tier = tier; }
        public void setRank(String rank) { this.rank = rank; }
        public void setLeaguePoints(int leaguePoints) { this.leaguePoints = leaguePoints; }
        public void setWins(int wins) { this.wins = wins; }
        public void setLosses(int losses) { this.losses = losses; }
        public void setHotStreak(boolean hotStreak) { this.hotStreak = hotStreak; }
        public void setVeteran(boolean veteran) { this.veteran = veteran; }
        public void setFreshBlood(boolean freshBlood) { this.freshBlood = freshBlood; }
        public void setInactive(boolean inactive) { this.inactive = inactive; }
    }



}
