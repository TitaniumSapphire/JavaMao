package com.mao.client;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.mao.Card;
import com.mao.Debug;
import com.mao.Game;
import com.mao.Lobby;
import com.mao.Network;
import com.mao.NetworkClient;
import com.mao.Player;
import com.mao.RuleHandler;
import com.mao.lang.Code;
import com.mao.lang.PenalizeCommand;
import com.mao.lang.SayCommand;

import processing.core.PApplet;

public class MainClient {
	public static String server;
	public static String username = "User123";
	public static Lobby lobby;
	public static Player player;

	public static void main(String[] args) throws IOException {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Processing.getProcessing().setGameState(-1);

				if (lobby != null) {
					if (lobby.getOwner().equals(username)) {
						Game.getGame().setGameOver(true);
						Game.getGame().update();
						Thread.sleep(1000);
					}

					Network.deinitialize();
					Thread.sleep(1000);
					Network.initialize(new NetworkClient(1338));
					Thread.sleep(1000);

					if (lobby.getOwner().equals(username)) {
						lobby.end();
					} else {
						lobby.leave(username);
					}

					lobby.update();

					Thread.sleep(1000);
				}

				Network.deinitialize();
				Thread.sleep(1000);
				Debug.log("Gracefully closed all connections, exiting process complete! Goodbye!");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}));

		server = JOptionPane.showInputDialog("Please enter the server's IP: ").trim();
		PApplet.main("com.mao.client.Processing");
	}

	public static List<String> callEvent(Player player, Card card, String event) {
		ArrayList<String> required = new ArrayList<>();
		for (Code response : RuleHandler.getRuleHandler().fire(event, player, Player.getCurrentTurnPlayer(),
				Player.getNextTurnPlayer(), card)) {
			if (response instanceof PenalizeCommand) {
				PenalizeCommand penalize = (PenalizeCommand) response;

				Card penalty = Game.getGame().getCardFromDeck();
				player.addCard(penalty);

				Processing.getProcessing().notify(penalize.getReason(), penalize.getReason(), Color.RED);
			} else if (response instanceof SayCommand) {
				SayCommand say = (SayCommand) response;
				required.add(say.getPhrase().toLowerCase().replace(".", "").trim());
			}
		}
		return required;
	}
}