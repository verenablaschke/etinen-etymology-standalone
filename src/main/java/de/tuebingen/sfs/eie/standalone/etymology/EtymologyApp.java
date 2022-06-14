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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tuebingen.sfs.eie.components.etymology.problems.EtymologyProblem;
import de.tuebingen.sfs.eie.components.etymology.problems.EtymologyProblemConfig;
import de.tuebingen.sfs.psl.engine.InferenceResult;
import de.tuebingen.sfs.psl.engine.ProblemManager;
import de.tuebingen.sfs.psl.engine.RuleAtomGraph;
import de.tuebingen.sfs.psl.gui.StandaloneFactWindowLauncher;
import de.tuebingen.sfs.psl.util.log.InferenceLogger;

public class EtymologyApp {
    // Germanic sample etymologies
    // (model this properly in the full EtInEn system to get proper phonetic similarity scores.)
    static String phyloGermanic = "src/main/resources/sampledata/germanic-tree.txt";
    static String etymTakeExample = "src/main/resources/sampledata/take-example.txt";

    // Sample data with complex contact network:
    static String phyloBasicExample = "src/main/resources/sampledata/languages.txt";
    // Sample data with only one contact:
    static String phyloLateDistantContact = "src/main/resources/sampledata/languages-late-distant-contact.txt";

    // Only inherited words:
    static String etymInherited = "src/main/resources/sampledata/inherited.txt";
    static String etymTwoSetsLeafLoan = "src/main/resources/sampledata/two-sets-leaf-loan.txt";
    static String etymTwoSetsLoanWithChild = "src/main/resources/sampledata/two-sets-loan-with-child.txt";
    static String etymOneSetInternalLoan = "src/main/resources/sampledata/one-set-internal-loan.txt";


    public static void main(String[] args) {
        boolean printExplanations = true;
        ProblemManager problemManager = ProblemManager.defaultProblemManager();
        EtymologyProblemConfig config = EtymologyProblemConfig.fromJson(new ObjectMapper(),
                "/src/main/resources/config.json", new InferenceLogger());
        config.setNonPersistableFeatures("EtymologyProblem", problemManager.getDbManager());
        EtymologyProblem problem = new EtymologyProblem(config);
        SampleIdeaGenerator ideaGen = new SampleIdeaGenerator(problem);
//		ideaGen.generateAtoms(new SampleData(phyloBasicExample, etymInherited));
//		ideaGen.generateAtoms(new SampleData(phyloBasicExample, etymTwoSetsLoanWithChild));
//		ideaGen.generateAtoms(new SampleData(phyloLateDistantContact, etymTwoSetsLeafLoan));
//		ideaGen.generateAtoms(new SampleData(phyloLateDistantContact, etymOneSetInternalLoan));
        ideaGen.generateAtoms(new SampleData(phyloGermanic, etymTakeExample));
        InferenceResult result = problemManager.registerAndRunProblem(problem);
        RuleAtomGraph rag = result.getRag();
        rag.printToStream(System.out);
        result.printInferenceValues();
        problem.printRules(System.out);
        config.print(System.out);
        StandaloneFactWindowLauncher.launchWithData(null, problem, result, true, printExplanations);
    }

}
