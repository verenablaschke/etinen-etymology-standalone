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

	public static void main(String[] args) {
		boolean printExplanations = true;
		ProblemManager problemManager = ProblemManager.defaultProblemManager();
		EtymologyProblemConfig config = EtymologyProblemConfig.fromJson(new ObjectMapper(),
				"/src/main/resources/config.json", new InferenceLogger());
		config.setNonPersistableFeatures("EtymologyProblem", problemManager.getDbManager());
		EtymologyProblem problem = new EtymologyProblem(config);
		SampleIdeaGenerator ideaGen = new SampleIdeaGenerator(problem);
		ideaGen.generateAtoms(new SampleData());
		InferenceResult result = problemManager.registerAndRunProblem(problem);
		RuleAtomGraph rag = result.getRag();
		rag.printToStream(System.out);
		result.printInferenceValues();
		problem.printRules(System.out);
		config.print(System.out);
		StandaloneFactWindowLauncher.launchWithData(null, problem, result, true, printExplanations);
	}

}
