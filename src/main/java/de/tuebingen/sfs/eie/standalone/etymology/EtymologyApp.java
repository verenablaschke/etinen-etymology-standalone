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
	static String phyloBasicExample = "src/main/resources/sampledata/languages.txt";
	static String phyloLateDistantContact = "src/main/resources/sampledata/languages-late-distant-contact.txt";
	static String phyloGermanic = "src/main/resources/sampledata/germanic-tree.txt";

	static String etymInherited = "src/main/resources/sampledata/inherited.txt";
	static String etymWithBorrowing = "src/main/resources/sampledata/wordtree.txt";
	static String etymTwoSetsOneLoan = "src/main/resources/sampledata/two-sets-one-loan.txt";
	static String etymTwoSetsOneLoanClearCase = "src/main/resources/sampledata/two-sets-one-loan-clear-case.txt";
	static String etymOneSetOneInternalLoanClearCase = "src/main/resources/sampledata/one-sets-one-internal-loan-clear-case.txt";

	static String etymTakeExample = "src/main/resources/sampledata/take-example.txt";

	public static void main(String[] args) {
		boolean printExplanations = true;
		ProblemManager problemManager = ProblemManager.defaultProblemManager();
		EtymologyProblemConfig config = EtymologyProblemConfig.fromJson(new ObjectMapper(),
				"/src/main/resources/config.json", new InferenceLogger());
		config.setNonPersistableFeatures("EtymologyProblem", problemManager.getDbManager());
		EtymologyProblem problem = new EtymologyProblem(config);
		SampleIdeaGenerator ideaGen = new SampleIdeaGenerator(problem);
//		ideaGen.generateAtoms(new SampleData(phyloBasicExample, etymInherited));
//		ideaGen.generateAtoms(new SampleData(phyloBasicExample, etymWithBorrowing));
//		ideaGen.generateAtoms(new SampleData(phyloBasicExample, etymTwoSetsOneLoan));
//		ideaGen.generateAtoms(new SampleData(phyloLateDistantContact, tymTwoSetsOneLoanClearCase));
//		ideaGen.generateAtoms(new SampleData(phyloLateDistantContact, etymOneSetOneInternalLoanClearCase));
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
