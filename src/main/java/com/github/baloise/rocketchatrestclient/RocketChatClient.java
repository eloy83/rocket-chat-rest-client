package com.github.baloise.rocketchatrestclient;

import static java.lang.String.format;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.github.baloise.rocketchatrestclient.model.Message;
import com.github.baloise.rocketchatrestclient.model.Room;
import com.github.baloise.rocketchatrestclient.model.Rooms;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class RocketChatClient {

	private final String serverUrl;
	private final String user;
	private final String password;
	private  String xAuthToken;
	private  String xUserId;
	private com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper;
	
	public RocketChatClient(String serverUrl, String user, String password) {
		this.serverUrl = serverUrl;
		this.user = user;
		this.password = password;
		jacksonObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
		jacksonObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
	
	Map<String, Room> roomCache = new HashMap<>();
	public Set<Room> getPublicRooms() throws IOException {
		 Rooms rooms = authenticatedGet("publicRooms", Rooms.class);
		 HashSet<Room> ret = new HashSet<>();
		 roomCache.clear();
		 for ( Room r : rooms.rooms) {
			ret.add(r);
			roomCache.put(r.name, r);
		}
		return ret;
	}

	private <T> T authenticatedGet(String method, Class<T> reponseClass) throws IOException {
		try {
		HttpResponse<String> ret = Unirest
				 .get(serverUrl+method)
				.header("X-Auth-Token", xAuthToken)
				.header("X-User-Id", xUserId)
				.asString();
		if(ret.getStatus() == 401) {
			login();
			return authenticatedGet(method, reponseClass);
		}
		return jacksonObjectMapper.readValue(ret.getBody(), reponseClass);
		} catch (UnirestException e) {
			throw new IOException(e);
		}
	}
	
	private void authenticatedPost(String method, Object request) throws IOException {
		authenticatedPost(method, request, null);
	}
	
	private <T> T authenticatedPost(String method, Object request, Class<T> reponseClass) throws IOException {
		try {
			HttpResponse<String> ret = Unirest.post(serverUrl+method)
					.header("X-Auth-Token", xAuthToken)
					.header("X-User-Id", xUserId)
					.header("Content-Type", "application/json")
					.body(jacksonObjectMapper.writeValueAsString(request))
					.asString();
			if(ret.getStatus() == 401) {
				login();
				return authenticatedPost(method, request, reponseClass);
			}
			return reponseClass == null ? null :jacksonObjectMapper.readValue(ret.getBody(), reponseClass);
		} catch (UnirestException e) {
			throw new IOException(e);
		}
	}

	void login() throws UnirestException {
		HttpResponse<JsonNode> asJson = Unirest.post(serverUrl +"login")
				.field("user", user)
				.field("password", password)
				 .asJson();
		if(asJson.getStatus() == 401) {
			throw new UnirestException("401 - Unauthorized");
		}
		JSONObject data = asJson.getBody().getObject().getJSONObject("data");
		xAuthToken = data.getString("authToken");
		xUserId = data.getString("userId");
	}

	public void logout() throws IOException {
		try {
			Unirest.post(serverUrl+"logout")
					.header("X-Auth-Token", xAuthToken)
					.header("X-User-Id", xUserId)
					.asJson();
		} catch (UnirestException e) {
			throw new IOException(e);
		}
	}

	public String getApiVersion() throws IOException {
		return getVersions().getString("api");
	}

	public String getRocketChatVersion() throws IOException {
		return getVersions().getString("rocketchat");
	}
	
	JSONObject lazyVersions;

	private JSONObject getVersions() throws IOException {
		if (lazyVersions == null) {
			try {
				lazyVersions = Unirest.get(serverUrl + "version").asJson().getBody().getObject()
						.getJSONObject("versions");
			} catch (UnirestException e) {
				throw new IOException(e);
			}
		}
		return lazyVersions;
	}

	public void send(String roomName, String messsage) throws IOException {
		Room room = getRoom(roomName);
		if(room == null) throw new IOException(format("unknown room : %s", roomName));
		send(room, messsage);
	}

	public void send(Room room, String messsage) throws IOException {
		authenticatedPost("rooms/"+room._id+"/send" , new Message(messsage));
	}

	public Room getRoom(String room) throws IOException {
		Room ret = roomCache.get(room);
		if(ret == null) {
			getPublicRooms();
			ret = roomCache.get(room);
		}
		return ret;
	}

}
