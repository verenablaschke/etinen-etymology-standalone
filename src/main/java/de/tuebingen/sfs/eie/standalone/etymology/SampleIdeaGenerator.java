package de.tuebingen.sfs.eie.standalone.etymology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import de.tuebingen.sfs.eie.components.etymology.ideas.EtymologyIdeaGenerator;
import de.tuebingen.sfs.eie.components.etymology.problems.EtymologyProblem;
import de.tuebingen.sfs.eie.shared.core.LanguagePhylogeny;
import de.tuebingen.sfs.eie.shared.util.Pair;
import de.tuebingen.sfs.psl.util.data.Multimap;
import de.tuebingen.sfs.psl.util.data.Multimap.CollectionType;

public class SampleIdeaGenerator extends EtymologyIdeaGenerator {

	static final double similarityDecay = 0.1;
	static final double minSim = 0.1;

	public SampleIdeaGenerator(EtymologyProblem problem) {
		super(problem, null);
	}

	public void generateAtoms(SampleData data) {
		Stack<String> langStack = new Stack<>();
		Multimap<String, String> langsToForms = new Multimap<>(CollectionType.SET);
		Set<String> homPegs = new HashSet<>();
		for (String formId : data.formsToPegs.keySet()) {
			String lang = data.formsToLangs.get(formId);
			langStack.add(lang);
			langsToForms.put(lang, formId);
			homPegs.add(data.formsToPegs.get(formId));
		}

		// Language atoms
		Set<String> langsAdded = new HashSet<>();
		String anc = data.phylo.lowestCommonAncestor(langStack);
		if (anc.equals(LanguagePhylogeny.root)) {
			// If the selection contains languages from multiple different families,
			// add languages + word forms from up to the earliest established contact.
			// TODO warn user if there are no relevant contacts
			Multimap<String, String> familyAncestorToLangs = new Multimap<>(CollectionType.SET);
			for (String lang : langStack) {
				familyAncestorToLangs.put(data.phylo.getPathFor(lang).get(0), lang);
			}
			for (Collection<String> relatedLangs : familyAncestorToLangs.values()) {
				langStack.clear();
				langStack.addAll(relatedLangs);
				anc = data.phylo.lowestCommonAncestor(langStack);
				addLanguageFamily(data.phylo, homPegs, anc, langStack, langsAdded, langsToForms);
			}
		} else {
			// If there is a (non-root) common ancestor,
			// add languages + word forms up to the lowest common ancestor.
			addLanguageFamily(data.phylo, homPegs, anc, langStack, langsAdded, langsToForms);
		}
		for (String lang : langsAdded) {
			if (data.phylo.hasIncomingInfluences(lang)) {
				for (String contact : data.phylo.getIncomingInfluences(lang)) {
					if (langsAdded.contains(contact)) {
						pslProblem.addObservation("Xloa", 1.0, lang, contact);
					}
				}
			}
		}

		// Form atoms
		// TODO check EtymologicalTheory to see if confirm Einh/Eloa/Eety belief values
		// from previous inferences can be used here
		for (String lang : langsToForms.keySet()) {
			for (String formId : langsToForms.get(lang)) {
				pslProblem.addTarget("Eunk", formId);

				if (data.phylo.hasIncomingInfluences(lang)) {
					for (String contact : data.phylo.getIncomingInfluences(lang)) {
						if (langsToForms.containsKey(contact)) {
							for (String contactFormId : langsToForms.get(contact)) {
								pslProblem.addObservation("Xloa", 1.0, formId, contactFormId);
								pslProblem.addTarget("Eloa", formId, contactFormId);
							}
						}
					}
				}
				String parent = data.phylo.parents.get(lang);
				if (langsToForms.containsKey(parent)) {
					for (String parentFormId : langsToForms.get(parent)) {
						pslProblem.addObservation("Xinh", 1.0, formId, parentFormId);
						pslProblem.addTarget("Einh", formId, parentFormId);
					}
				}
			}
		}

		List<String> allForms = new ArrayList<>();
		langsToForms.values().forEach(allForms::addAll);
		for (int i = 0; i < allForms.size() - 1; i++) {
			String formIdI = allForms.get(i);
			pslProblem.addObservation("Fsim", 1.0, formIdI, formIdI);
			addHomsetInfo(formIdI, homPegs, data.formsToPegs);

			// Compare phonetic forms.
			boolean hasUnderlyingForm1 = data.knownForms.contains(formIdI);
			for (int j = i + 1; j < allForms.size(); j++) {
				String formIdJ = allForms.get(j);
				if (!hasUnderlyingForm1 || !data.knownForms.contains(formIdJ)) {
					pslProblem.addTarget("Fsim", formIdI, formIdJ);
					pslProblem.addTarget("Fsim", formIdJ, formIdI);
				} else {
					double fSim = distToExpectedSim(data.formDistances.get(new Pair<>(formIdI, formIdJ)));
					pslProblem.addObservation("Fsim", fSim, formIdI, formIdJ);
					pslProblem.addObservation("Fsim", fSim, formIdJ, formIdI);
				}
			}
		}
		String lastForm = allForms.get(allForms.size() - 1);
		addHomsetInfo(lastForm, homPegs, data.formsToPegs);
		pslProblem.addObservation("Fsim", 1.0, lastForm, lastForm);

		System.err.println("Forms and languages");
		for (String form : allForms) {
			System.err.println("Form: " + form + " " + data.formsToLangs.get(form));
		}
		System.err.println("Forms and homologue pegs");
		for (String form : homPegs) {
			System.err.println("Peg: " + form + " " + data.formsToPegs.get(form));
		}

		if (PRINT_LOG) {
			super.pslProblem.printAtomsToConsole();
		}
	}

	private void addLanguageFamily(LanguagePhylogeny phylo, Set<String> homPegs, String lowestCommonAnc,
			Stack<String> langStack, Set<String> langsAdded, Multimap<String, String> langsToForms) {
		logger.displayln("Adding languages descended from " + lowestCommonAnc + ":");
		while (!langStack.isEmpty()) {
			String lang = langStack.pop();
			if (langsAdded.contains(lang)) {
				continue;
			}
			logger.displayln("- " + lang);
			langsAdded.add(lang);
			String parent = phylo.parents.get(lang);
			pslProblem.addObservation("Xinh", 1.0, lang, parent);
			if (!parent.equals(lowestCommonAnc) && !langsAdded.contains(parent)) {
				langStack.push(parent);
			}
		}
	}

	private void addHomsetInfo(String formId, Set<String> homPegs, Map<String, String> formsToPegs) {
		String pegForForm = formsToPegs.get(formId);
		System.err.println("PEG: " + formId + " " + pegForForm);
		if (pegForForm == null) {
			for (String homPeg : homPegs) {
				pslProblem.addTarget("Fhom", formId, homPeg);
				System.err.println("Fhom(" + formId + ", " + homPeg + ")");
			}
		} else {
			for (String homPeg : homPegs) {
				if (homPeg.equals(pegForForm)) {
					pslProblem.addObservation("Fhom", 1.0, formId, homPeg);
					System.err.println("Fhom(" + formId + ", " + homPeg + ") 1.0");
				} else {
					pslProblem.addObservation("Fhom", 0.0, formId, homPeg);
					System.err.println("Fhom(" + formId + ", " + homPeg + ") 0.0");
				}
			}
		}
	}

	public static double distToExpectedSim(int dist) {
		double sim = 1 - similarityDecay * dist;
		return sim < minSim ? minSim : sim;
	}

}
