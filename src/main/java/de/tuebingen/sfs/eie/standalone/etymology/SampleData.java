/*
 * Copyright 2018–2022 University of Tübingen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tuebingen.sfs.eie.standalone.etymology;

import de.tuebingen.sfs.eie.shared.core.LanguagePhylogeny;
import de.tuebingen.sfs.eie.shared.core.LanguageTree;
import de.tuebingen.sfs.eie.shared.util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SampleData {

	static int inhDist = 1;
	static int loanDist = 0;

	// In lieu of using actual phonetic distances, this class infers expected distances from the word trees:
	Map<Pair<String, String>, Integer> formDistances = new HashMap<>();
	Set<String> forms = new HashSet<>();
	LanguagePhylogeny phylo = new LanguagePhylogeny(new LanguageTree());
	Set<String> knownForms = new HashSet<>();
	Map<String, String> formsToPegs = new HashMap<>();
	Map<String, String> formsToLangs = new HashMap<>();

	public SampleData(String treeFile, String etymologyFile) {
		readTree(treeFile);
		System.err.println(phylo.toNewickString());
		readEtymologies(etymologyFile);

		// For now: known forms = leaves
		for (String form : forms) {
			if (!phylo.children.containsKey(formsToLangs.get(form))) {
				knownForms.add(form);
			}
		}

		System.err.println("FORMS");
		System.err.println(forms);
		System.err.println();
		System.err.println("FORM DISTANCES");
		System.err.println(formDistances);
		System.err.println();
		System.err.println("KNOWN FORMS");
		System.err.println(knownForms);
		System.err.println();
		System.err.println("FORMS TO PEGS");
		System.err.println(formsToPegs);
		System.err.println();
		System.err.println("FORMS TO LANGS");
		System.err.println(formsToLangs);
		System.err.println();
	}

	private void readEtymologies(String file) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			Map<String, String> formToSource = new HashMap<>();

			processEtymologyFile(reader, formToSource);

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

			System.err.println("EXPECTED DISTANCES");
			List<String> forms = new ArrayList<>();
			forms.addAll(formsToLangs.keySet());
			Collections.sort(forms);
			System.out.println("     " + String.join("   ", forms));
			for (int i = 0; i < forms.size(); i++) {
				String lang1 = formsToLangs.get(forms.get(i));
				System.out.print(lang1 + "  ");
				if (lang1.length() < 3) {
					System.out.print(" ");
				}
				for (int j = 0; j < forms.size(); j++) {
					if (i > j) {
						System.out.print("     ");
					} else {
						System.out.printf(
								"%.1f  ",
								SampleIdeaGenerator.distToExpectedSim(formDistances.get(new Pair<>(forms.get(i), forms.get(j)))));
					}
				}
				System.out.println();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processEtymologyFile(BufferedReader reader, Map<String, String> formToSource) throws IOException {
		Stack<String> sourceStack = new Stack<>();
		sourceStack.push(LanguageTree.root);
		formDistances.put(new Pair<>(LanguageTree.root, LanguageTree.root), 0);
		String currentPeg = null;
		int prevIndent = -1;
		int curIndent;
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.isBlank())
				continue;
			for (curIndent = 0; curIndent < line.length() - 1; curIndent++) {
				if (line.charAt(curIndent) != ' ') {
					break;
				}
			}

			// Language tree link
			String form = line.replace("->", "").strip();
			int distToSource = line.contains("->") ? loanDist : inhDist;
			forms.add(form);
			if (form.contains(":")) {
				formsToLangs.put(form, form.substring(0,form.indexOf(":")));
			} else {
				formsToLangs.put(form, form.replace("w", "L"));
			}

			while (curIndent <= prevIndent--) {
				sourceStack.pop();
			}
			String source = sourceStack.peek();
			formToSource.put(form, source);
			if (source.equals(LanguageTree.root)) {
				distToSource = 8;
				currentPeg = form;
			}
			formDistances.put(new Pair<>(form, source), distToSource);
			formDistances.put(new Pair<>(source, form), distToSource);
			formDistances.put(new Pair<>(form, form), 0);
			int distSourceToRoot = formDistances.get(new Pair<>(source, LanguageTree.root));
			formDistances.put(new Pair<>(form, LanguageTree.root), distSourceToRoot + distToSource);
			formDistances.put(new Pair<>(LanguageTree.root, form), distSourceToRoot + distToSource);
			sourceStack.push(form);
			formsToPegs.put(form,currentPeg);
			prevIndent = curIndent;
		}
	}

	private void readTree(String treeFile) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(treeFile), StandardCharsets.UTF_8))) {
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
		List<String> path1 = new ArrayList<>();
		path1.add(node1);
		String anc = node1;
		while (formToSource.containsKey(anc)) {
			anc = formToSource.get(anc);
			path1.add(anc);
		}
		List<String> path2 = new ArrayList<>();
		path2.add(node2);
		anc = node2;
		while (formToSource.containsKey(anc)) {
			anc = formToSource.get(anc);
			path2.add(anc);
		}
		for (String anc2 : path2) {
			if (path1.contains(anc2)) {
				return anc2;
			}
		}
		return LanguageTree.root;
	}

}
