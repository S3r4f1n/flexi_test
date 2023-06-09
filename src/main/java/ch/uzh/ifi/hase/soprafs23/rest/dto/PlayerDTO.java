package ch.uzh.ifi.hase.soprafs23.rest.dto;

public class PlayerDTO {

    private String name;
    private String token;

    public PlayerDTO(String name, String token) {
        this.name = name;
        this.token = token;
    }
    public PlayerDTO(){}

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String username) {
        this.name = username;
    }
}
