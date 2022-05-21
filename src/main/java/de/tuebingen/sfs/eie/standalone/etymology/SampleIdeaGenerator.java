package de.tuebingen.sfs.eie.standalone.etymology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import de.tuebingen.sfs.eie.components.etymology.ideas.EtymologyIdeaGenerator;
import de.tuebingen.sfs.eie.components.etymology.problems.EtymologyProblem;
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
		Set<String> homPegs = new HashSet<>();
		for (String formId : data.formsToPegs.keySet()) {
			String lang = data.formsToLangs.get(formId);
			langStack.add(lang);
			homPegs.add(data.formsToPegs.get(formId));
		}

		Multimap<String, String> langsToForms = new Multimap<>(CollectionType.SET);
		for (Entry<String, String> entry : data.formsToLangs.entrySet()) {
			langsToForms.put(entry.getValue(), entry.getKey());
		}

		// Form atoms
		// TODO check EtymologicalTheory to see if confirm Einh/Eloa/Eety belief values
		// from previous inferences can be used here
		int maxDist = -1;
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
				System.err.println(parent + " " + langsToForms.containsKey(parent) + " " + langsToForms.keySet());
				if (langsToForms.containsKey(parent)) {
					for (String parentFormId : langsToForms.get(parent)) {
						pslProblem.addObservation("Xinh", 1.0, formId, parentFormId);
						pslProblem.addTarget("Einh", formId, parentFormId);
					}
				}
			}
			for (String lang2 : langsToForms.keySet()) {
				int dist = data.phylo.distance(lang, lang2);
				if (dist > maxDist) {
					maxDist = dist;
				}
				for (String formId1 : langsToForms.get(lang)) {
					for (String formId2 : langsToForms.get(lang2)) {
						pslProblem.addObservation("Xdst", 1.0, formId1, formId2, dist + "");
					}
				}
			}
		}
		for (int i = maxDist; i > 0; i--) {
			for (int j = i - 1; j >= 0; j--) {
				pslProblem.addObservation("Xsth", 1.0, j + "", i + "");
			}
		}

		List<String> allForms = new ArrayList<>();
		data.formsToLangs.keySet().forEach(allForms::add);
		for (int i = 0; i < allForms.size() - 1; i++) {
			String formIdI = allForms.get(i);
			pslProblem.addObservation("Fsim", 1.0, formIdI, formIdI);
			((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formIdI, formIdI);
			boolean hasUnderlyingForm1 = data.knownForms.contains(formIdI);
			addHomsetInfo(formIdI, homPegs, data.formsToPegs, hasUnderlyingForm1);
			System.err.println("Adding Fsim(" + formIdI + ", " + formIdI + ") 1.0"); // TODO del

			// Make sure the EinhOrEloaOrEunk rule always gets grounded:
			pslProblem.addObservation("Eloa", 0.0, formIdI, "eloaCtrl");
			// TODO this one should actually probably be properly excluded from the sidebar:
			((EtymologyProblem) pslProblem).addFixedAtom("Eloa", formIdI, "eloaCtrl");

			// Compare phonetic forms.
			for (int j = i + 1; j < allForms.size(); j++) {
				String formIdJ = allForms.get(j);
				if (!hasUnderlyingForm1 || !data.knownForms.contains(formIdJ)) {
					pslProblem.addTarget("Fsim", formIdI, formIdJ);
					pslProblem.addTarget("Fsim", formIdJ, formIdI);
					System.err.println("Adding Fsim(" + formIdI + ", " + formIdJ + ") ?"); // TODO del
					System.err.println("Adding Fsim(" + formIdJ + ", " + formIdI + ") ?"); // TODO del
				} else {
					double fSim = distToExpectedSim(data.formDistances.get(new Pair<>(formIdI, formIdJ)));
					pslProblem.addObservation("Fsim", fSim, formIdI, formIdJ);
					pslProblem.addObservation("Fsim", fSim, formIdJ, formIdI);
					System.err.println("Adding Fsim(" + formIdI + ", " + formIdJ + ") " + fSim); // TODO del
					System.err.println("Adding Fsim(" + formIdJ + ", " + formIdI + ") " + fSim); // TODO del
					((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formIdI, formIdJ);
					((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formIdJ, formIdI);
				}
			}
		}
		String lastForm = allForms.get(allForms.size() - 1);
		addHomsetInfo(lastForm, homPegs, data.formsToPegs, data.knownForms.contains(lastForm));
		pslProblem.addObservation("Fsim", 1.0, lastForm, lastForm);
		pslProblem.addObservation("Eloa", 0.0, lastForm, "eloaCtrl");
		((EtymologyProblem) pslProblem).addFixedAtom("Fsim", lastForm, lastForm);
		// TODO see above
		((EtymologyProblem) pslProblem).addFixedAtom("Fsim", lastForm, "eloaCtrl");

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

	private void addHomsetInfo(String formId, Set<String> homPegs, Map<String, String> formsToPegs, boolean knownForm) {
		String pegForForm = formsToPegs.get(formId);
		System.err.println("PEG: " + formId + " " + pegForForm);
		// Currently assuming there's just one concept: C1
		if (pegForForm == null) {
			for (String homPeg : homPegs) {
				pslProblem.addTarget("Fhom", formId, homPeg, "C1");
				((EtymologyProblem) pslProblem).addFixedAtom("Fhom", formId, homPeg, "C1");
				System.err.println("Fhom(" + formId + ", " + homPeg + ", C1)");
			}
		} else {
			for (String homPeg : homPegs) {
				if (knownForm) {
					if (homPeg.equals(pegForForm)) {
						pslProblem.addObservation("Fhom", 1.0, formId, homPeg, "C1");
						System.err.println("Fhom(" + formId + ", " + homPeg + ", C1) 1.0");

					} else {
						pslProblem.addObservation("Fhom", 0.0, formId, homPeg, "C1");
						System.err.println("Fhom(" + formId + ", " + homPeg + ", C1) 0.0");
					}
					((EtymologyProblem) pslProblem).addFixedAtom("Fhom", formId, homPeg, "C1");
				} else {
					pslProblem.addTarget("Fhom", formId, homPeg, "C1");
				}
			}
		}
	}

	public static double distToExpectedSim(int dist) {
		double sim = 1 - similarityDecay * dist;
		return sim < minSim ? minSim : sim;
	}

}
