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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
        Set<String> homPegs = new HashSet<>();
        for (String formId : data.formsToPegs.keySet()) {
            homPegs.add(data.formsToPegs.get(formId));
        }

        Multimap<String, String> langsToForms = new Multimap<>(CollectionType.SET);
        for (Entry<String, String> entry : data.formsToLangs.entrySet()) {
            langsToForms.put(entry.getValue(), entry.getKey());
        }

        // Form atoms
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
        allForms.addAll(data.formsToLangs.keySet());
        for (int i = 0; i < allForms.size() - 1; i++) {
            String formIdI = allForms.get(i);
            boolean hasUnderlyingForm1 = addAtomsForSingleForm(formIdI, data, homPegs);

            // Compare phonetic forms.
            for (int j = i + 1; j < allForms.size(); j++) {
                String formIdJ = allForms.get(j);
                if (!hasUnderlyingForm1 || !data.knownForms.contains(formIdJ)) {
                    pslProblem.addTarget("Fsim", formIdI, formIdJ);
                    pslProblem.addTarget("Fsim", formIdJ, formIdI);
                } else {
                    double fSim = distToExpectedSim(data.formDistances.get(new Pair<>(formIdI, formIdJ)));
                    pslProblem.addObservation("Fsim", fSim, formIdI, formIdJ);
                    pslProblem.addObservation("Fsim", fSim, formIdJ, formIdI);
                    ((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formIdI, formIdJ);
                    ((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formIdJ, formIdI);
                }
            }
        }
        addAtomsForSingleForm(allForms.get(allForms.size() - 1), data, homPegs);

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

    private boolean addAtomsForSingleForm(String formId, SampleData data, Set<String> homPegs) {
        pslProblem.addObservation("Fsim", 1.0, formId, formId);
        ((EtymologyProblem) pslProblem).addFixedAtom("Fsim", formId, formId);
        boolean hasUnderlyingForm1 = data.knownForms.contains(formId);
        addHomsetInfo(formId, homPegs, data.formsToPegs, hasUnderlyingForm1);

        // Make sure the EinhOrEloaOrEunk rule always gets grounded:
        pslProblem.addObservation("Eloa", 0.0, formId, "eloaCtrl");
        ((EtymologyProblem) pslProblem).addFixedAtom("Eloa", formId, "eloaCtrl");
        ((EtymologyProblem) pslProblem).addHiddenAtom("Eloa", formId, "eloaCtrl");

        return hasUnderlyingForm1;
    }

    private void addHomsetInfo(String formId, Set<String> homPegs, Map<String, String> formsToPegs, boolean knownForm) {
        String pegForForm = formsToPegs.get(formId);
        System.err.println("PEG: " + formId + " " + pegForForm);
        if (pegForForm == null) {
            for (String homPeg : homPegs) {
                pslProblem.addTarget("Fhom", formId, homPeg);
                ((EtymologyProblem) pslProblem).addFixedAtom("Fhom", formId, homPeg);
                System.err.println("Fhom(" + formId + ", " + homPeg + ")");
            }
        } else {
            for (String homPeg : homPegs) {
                if (knownForm) {
                    if (homPeg.equals(pegForForm)) {
                        pslProblem.addObservation("Fhom", 1.0, formId, homPeg);
                        System.err.println("Fhom(" + formId + ", " + homPeg + ") 1.0");

                    } else {
                        pslProblem.addObservation("Fhom", 0.0, formId, homPeg);
                        System.err.println("Fhom(" + formId + ", " + homPeg + ") 0.0");
                    }
                    ((EtymologyProblem) pslProblem).addFixedAtom("Fhom", formId, homPeg);
                } else {
                    pslProblem.addTarget("Fhom", formId, homPeg);
                }
            }
        }
    }

    public static double distToExpectedSim(int dist) {
        double sim = 1 - similarityDecay * dist;
        return sim < minSim ? minSim : sim;
    }

}
