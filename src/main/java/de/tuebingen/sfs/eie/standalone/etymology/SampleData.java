package de.tuebingen.sfs.eie.standalone.etymology;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import de.tuebingen.sfs.eie.shared.core.LanguagePhylogeny;
import de.tuebingen.sfs.eie.shared.core.LanguageTree;
import de.tuebingen.sfs.eie.shared.util.Pair;

public class SampleData {

	Map<Pair<String, String>, Integer> formDistances = new HashMap<>();
	Set<String> forms = new HashSet<>();
	LanguagePhylogeny phylo = new LanguagePhylogeny(new LanguageTree());
	Set<String> knownForms = new HashSet<>();
	Map<String, String> formsToPegs = new HashMap<>();
	Map<String, String> formsToLangs = new HashMap<>();

	public SampleData() {
		readTree("src/main/resources/sampledata/languages.txt");
		readEtymology("src/main/resources/sampledata/wordtree.txt");

		// For now: known forms = leaves
		for (String form : forms) {
			if (!phylo.children.containsKey(formsToLangs.get(form))) {
				knownForms.add(form);
			}
		}

		// For now: assume we're dealing with a single homset
		String peg = knownForms.iterator().next();
		for (String knownForm : knownForms) {
			formsToPegs.put(knownForm, peg);
		}
	}

	private void readEtymology(String file) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(file)), StandardCharsets.UTF_8))) {
			Map<String, String> formToSource = new HashMap<>();
			Stack<String> sourceStack = new Stack<>();
			sourceStack.push(LanguageTree.root);
			formDistances.put(new Pair<>(LanguageTree.root, LanguageTree.root), 0);
			int prevIndent = -1;
			int curIndent;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.isEmpty())
					continue;
				for (curIndent = 0; curIndent < line.length() - 1; curIndent++) {
					if (line.charAt(curIndent) != ' ') {
						break;
					}
				}

				// Language tree link
				String form = line.replace("->", "").strip();
				forms.add(form);
				formsToLangs.put(form, form.replace("w", "L"));
				formDistances.put(new Pair<>(form, LanguageTree.root), curIndent + 1);
				formDistances.put(new Pair<>(LanguageTree.root, form), curIndent + 1);

				int indentMod = 0;
				while (curIndent + indentMod++ <= prevIndent) {
					sourceStack.pop();
				}
				String source = sourceStack.peek();
				formToSource.put(form, source);
				formDistances.put(new Pair<>(form, source), 1);
				formDistances.put(new Pair<>(source, form), 1);
				formDistances.put(new Pair<>(form, form), 0);
				sourceStack.push(form);
				prevIndent = curIndent;
			}

			for (String form1 : formToSource.keySet()) {
				for (String form2 : formToSource.keySet()) {
					if (formDistances.containsKey(new Pair<>(form1, form2))) {
						continue;
					}
					int dist = formDistances.get(new Pair<>(form1, LanguageTree.root))
							+ formDistances.get(new Pair<>(form2, LanguageTree.root)) - 2 * formDistances.get(
									new Pair<>(lowestCommonAncestor(form1, form2, formToSource), LanguageTree.root));
					formDistances.put(new Pair<>(form1, form2), dist);
					formDistances.put(new Pair<>(form2, form1), dist);
				}
			}

//			// TODO
//			String[] forms = new String[] { "w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8", "w9", "w10", "w11",
//					"w12", };
//			System.out.println("     " + String.join("   ", forms));
//			for (int i = 0; i < forms.length; i++) {
//				String lang1 = forms[i];
//				System.out.print(lang1 + "  ");
//				if (lang1.length() < 3) {
//					System.out.print(" ");
//				}
//				for (int j = 0; j < forms.length; j++) {
//					if (i > j) {
//						System.out.print("     ");
//					} else {
//						String lang2 = forms[j];
//						System.out.print("%.1f  ".formatted(
//								SampleIdeaGenerator.distToExpectedSim(distances.get(new Pair<>(lang1, lang2)))));
//					}
//				}
//				System.out.println();
//			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readTree(String treeFile) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(treeFile)), StandardCharsets.UTF_8))) {
			Stack<String> parentStack = new Stack<>();
			parentStack.push(LanguageTree.root);
			int prevIndent = -1;
			int curIndent;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.isEmpty())
					continue;
				for (curIndent = 0; curIndent < line.length() - 1; curIndent++) {
					if (line.charAt(curIndent) != ' ') {
						break;
					}
				}

				// Language tree link
				String[] langs = line.split("<-");
				String lang = langs[0].strip();

				int indentMod = 0;
				while (curIndent + indentMod++ <= prevIndent) {
					parentStack.pop();
				}
				String parent = parentStack.peek();
				phylo.parents.put(lang, parent);
				List<String> siblings = phylo.children.get(parent);
				if (siblings == null) {
					siblings = new ArrayList<>();
				}
				siblings.add(lang);
				phylo.children.put(parent, siblings);
				parentStack.push(lang);
				prevIndent = curIndent;

				// Contacts
				if (langs.length > 1) {
					for (String contact : langs[1].split(",")) {
						phylo.addInfluence(contact.strip(), lang);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String lowestCommonAncestor(String node1, String node2, Map<String, String> formToSource) {
		List<String> ancestors1 = new ArrayList<>();
		String anc = node1;
		while (formToSource.containsKey(anc)) {
			anc = formToSource.get(anc);
			ancestors1.add(anc);
		}
		List<String> ancestors2 = new ArrayList<>();
		anc = node2;
		while (formToSource.containsKey(anc)) {
			anc = formToSource.get(anc);
			ancestors2.add(anc);
		}
		for (String anc2 : ancestors2) {
			if (ancestors1.contains(anc2)) {
				return anc2;
			}
		}
		return LanguageTree.root;
	}

}
