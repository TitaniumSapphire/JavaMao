package com.mao;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mao.client.MainClient;
import com.mao.client.Speech;
import com.mao.lang.Code;
import com.mao.lang.Event;
import com.mao.lang.PenalizeCommand;
import com.mao.lang.Program;
import com.mao.lang.SayCommand;

import voce.SpeechInterface;

public class RuleHandler extends NetworkedObject {
	private static HashMap<String, RuleHandler> instances = new HashMap<>();

	public static RuleHandler getRuleHandler(String lobby) {
		return instances.get(lobby);
	}

	public static RuleHandler getRuleHandler() {
		if (Network.isClient()) {
			return getRuleHandler(MainClient.lobby.getName());
		} else if (!Network.isInitialized()) {
			throw new RuntimeException(
					"A getRuleHandler() call was attempted, but the network has not been initialized. This is likely not an error.");
		}
		throw new RuntimeException(
				"getRuleHandler() can only be called on a client; use getRuleHandler(String) instead");
	}

	public static void setRuleHandler(String lobby, RuleHandler object) {
		instances.put(lobby, object);
	}

	public static RuleHandler initialize(String lobby) {
		RuleHandler game = new RuleHandler();
		instances.put(lobby, game);
		Network.getNetwork().registerObject(game);
		return game;
	}

	private ArrayList<Program> rules = new ArrayList<>();

	public void addRule(Program rule) {
		rules.add(rule);
	}

	public List<Code> fire(String name, Player placer, Player shouldPlaced, Player next, Card card) {
		List<Code> responses = new ArrayList<>();
		for (Program rule : rules) {
			if (rule.handlesEvent(name)) {
				Event event = rule.getEvent(name);
				setupEvent(event, placer, shouldPlaced, next, card);

				Code result = event.execute().getSource();
				if (result instanceof SayCommand || result instanceof PenalizeCommand) {
					responses.add(result);
				}
			}
		}
		return responses;
	}

	private void setupEvent(Event event, Player placer, Player shouldPlaced, Player next, Card card) {
		event.getVariable("card").setValue(card);
		event.getVariable("player").setValue(placer);
		event.getVariable("actualPlayer").setValue(shouldPlaced);
		event.getVariable("nextPlayer").setValue(next);
		event.getVariable("playedCards").setValue(Game.getGame().getPlayedCards());
	}

	@Override
	public int getNetworkID() {
		return 2;
	}

	@Override
	public NetworkedData writeNetworkedData() {
		NetworkedData data = new NetworkedData();
		data.write(rules.size());
		for (int i = 0; i < rules.size(); i++) {
			rules.get(i).writeToNetworkData(data);
		}
		return data;
	}

	@Override
	public void readNetworkedData(NetworkedData data) {
		rules.clear();

		if (Network.isClient()) {
			SpeechInterface.destroy();
		}

		try {
			FileWriter out = null;
			if (Network.isClient()) {
				File grammar = new File(Speech.GRAMMAR_PATH + File.separator + Speech.GRAMMAR_NAME + ".gram");
				grammar.delete();
				grammar.createNewFile();

				out = new FileWriter(grammar, true);
				out.write("#JSGF V1.0;" + System.lineSeparator());
				out.write("grammar mao;" + System.lineSeparator());
				out.write("public <mao_gramar> = ");
			}

			int rulesSize = data.read();
			for (int i = 0; i < rulesSize; i++) {
				Program program = Program.readFromNetworkData(data);
				if (Network.isClient()) {
					for (String saying : program.getRegisteredSayings()) {
						out.write(saying.toLowerCase().replace(".", "").replace(",", "").replace("?", "")
								.replace("!", "").trim()
								+ (i == rulesSize - 1 && program.getRegisteredSayings()
										.indexOf(saying) == program.getRegisteredSayings().size() - 1 ? ";" : " | "));
					}
				}
				addRule(program);
			}

			if (Network.isClient()) {
				out.flush();
				out.close();
			}
		} catch (IOException e) {
			Debug.error("Error while updating rules!", e);
		}

		Debug.log("Rules updated. There are now " + rules.size() + " rules in effect.");

		if (Network.isClient()) {
			Speech.initialize();
		}
	}
}
