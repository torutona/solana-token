package org.jarcheck;

import org.jarcheck.analyzer.Analyzer;
import org.jarcheck.model.Classpath;
import org.jarcheck.report.Report;
import org.jarcheck.report.ReportSection;

import java.util.Arrays;
import java.util.List;

class Analysis {

	private final List<Analyzer> analyzers;

	Analysis(Analyzer... analyzers) {
		this.analyzers = Arrays.asList(analyzers);
	}

	Report run(Classpath classpath) {
		Report report = new Report();
		for (Analyzer analyzer : analyzers) {
			ReportSection section = analyzer.analyze(classpath);
			report.addSection(section);
		}
		return report;
	}

}