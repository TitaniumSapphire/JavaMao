package com.mao.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mao.Debug;
import com.mao.Network;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;

public class Processing extends PApplet {
	private static final long serialVersionUID = 4080175460722677681L;

	public static final int GAME_STATE_MAIN_MENU = 0;
	public static final int GAME_STATE_LOBBY_MENU = 1;
	public static final int GAME_STATE_JOIN_LOBBY = 2;
	public static final int GAME_STATE_JOINED_LOBBY = 3;
	public static final int GAME_STATE_CREATE_LOBBY = 4;
	public static final int GAME_STATE_CREATED_LOBBY = 5;
	public static final int GAME_STATE_IN_GAME = 6;

	private int gameState = GAME_STATE_MAIN_MENU;
	private UIState uiState = new UIMainMenu();

	private HashMap<Long, UIObject> objects = new HashMap<>();
	private HashMap<String, PImage> images = new HashMap<>();
	private HashMap<Integer, UIState> states = new HashMap<>();
	private ArrayList<UIObject> waitingObjects = new ArrayList<>();
	private Object lock = new Object();
	private boolean shouldLock = true;

	public Processing() {
		instance = this;
	}

	public void addUIObject(UIObject object) {
		synchronized (objects) {
			objects.put(object.getID(), object);
			sortObjects();
		}

		if (shouldLock) {
			waitingObjects.add(object);
		} else {
			object.initialize(this);
		}
	}

	public void removeUIObject(UIObject object) {
		synchronized (objects) {
			objects.remove(object.getID());
			sortObjects();
		}
	}

	public UIObject removeUIObject(long id) {
		synchronized (objects) {
			UIObject object = objects.remove(id);
			sortObjects();
			return object;
		}
	}

	public void sortObjects() {
		synchronized (objects) {
			objects = (HashMap<Long, UIObject>) sortByValue(objects);
		}
	}

	public UIObject getObject(long id) {
		return objects.get(id);
	}

	public PImage getImage(String name) {
		return images.get(name);
	}

	public PImage reloadImage(String name) {
		PImage image = loadImage("assets/images/" + name + ".png");
		images.put(name, image);
		return image;
	}

	public void notify(String message, String otherMessage, Color color) {
		addUIObject(new UINotification(message, color));

		if (otherMessage != null) {
			NetworkedNotification notification = new NetworkedNotification(MainClient.player.getUsername() + ": " + otherMessage,
					color == Color.RED ? Color.GREEN : Color.RED);
			Network.getNetworkClient().registerObject(notification);
			Network.getNetworkClient().makeUpdate(notification);
		}
	}

	@Override
	public void setup() {
		frame.setTitle("The Game of Mao");
		frame.setResizable(true);

		File folder = new File(System.getenv("IS_IN_IDE") != null ? "src/data/assets/images/" : "assets/images");
		for (File image : folder.listFiles()) {
			if (image.getName().endsWith("png")) {
				images.put(image.getName().split(Pattern.quote("."))[0], loadImage("assets/images/" + image.getName()));
			}
		}
		Debug.log("Loaded {0} images.", images.size());
		synchronized (lock) {
			lock.notifyAll();
		}

		for (UIObject object : waitingObjects) {
			object.initialize(this);
		}
		Debug.log("Initialized waiting objects.");
		waitingObjects.clear();
		shouldLock = false;

		states.put(GAME_STATE_MAIN_MENU, uiState);
		states.put(GAME_STATE_JOINED_LOBBY, new UIJoinedLobby());
		states.put(GAME_STATE_JOIN_LOBBY, new UILobbyPrompt());
		states.put(GAME_STATE_CREATE_LOBBY, new UICreateLobby());
		states.put(GAME_STATE_CREATED_LOBBY, new UICreatedLobby());
		states.put(GAME_STATE_LOBBY_MENU, new UILobbyList());
		states.put(GAME_STATE_IN_GAME, new UIGame());

		Screen.setSize(new Dimension(width, height));
		uiState.createObjects(this);
	}

	@Override
	public void draw() {
		background(255);
		textFont(new PFont(PFont.findFont("Courier"), true));

		Screen.setSize(new Dimension(width, height));

		if (shouldLock) {
			synchronized (lock) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		synchronized (objects) {
			UIObject[] values = objects.values().toArray(new UIObject[objects.size()]);
			for (UIObject object : values) {
				object.draw(this);
			}
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		synchronized (objects) {
			UIObject[] values = objects.values().toArray(new UIObject[objects.size()]);
			Arrays.sort(values, Comparator.reverseOrder());
			for (UIObject object : values) {
				if (object.mousePressed(e, this)) {
					break;
				}
			}
		}
	}

	@Override
	public void mouseReleased() {
		synchronized (objects) {
			UIObject[] values = objects.values().toArray(new UIObject[objects.size()]);
			for (UIObject object : values) {
				object.mouseReleased(this);
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		synchronized (objects) {
			UIObject[] values = objects.values().toArray(new UIObject[objects.size()]);
			for (UIObject object : values) {
				object.keyPressed(e);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		synchronized (objects) {
			UIObject[] values = objects.values().toArray(new UIObject[objects.size()]);
			for (UIObject object : values) {
				object.keyReleased(e);
			}
		}
	}

	public int getGameState() {
		return gameState;
	}

	public void setGameState(int gameState) {
		this.gameState = gameState;
		this.objects.clear();
		this.waitingObjects.clear();

		UIState state = states.get(gameState);
		if (state != null) {
			state.createObjects(this);
		}
	}

	// thanks to @CarterPage on StackOverflow
	private static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		return map.entrySet().stream().sorted(Map.Entry.comparingByValue())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	private static Processing instance;

	public static Processing getProcessing() {
		return instance;
	}
}
