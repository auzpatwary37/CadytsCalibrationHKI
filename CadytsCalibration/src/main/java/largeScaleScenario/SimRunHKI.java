package largeScaleScenario;

import java.io.IOException;
import java.util.LinkedHashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.builder.SignalsModule;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParser;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.AnaModelCalibrationModule;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;

public class SimRunHKI implements SimRun{
	private final int lastIteration=150;

	@Override
	public void run(AnalyticalModel sue, Config config, LinkedHashMap<String, Double> params, boolean generateOd,
			String threadNo, MeasurementsStorage storage) {
		
		config.controler().setLastIteration(this.lastIteration);
		config.controler().setOutputDirectory("LargeScaleOutput/output"+threadNo);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser;
		
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("data/LargeScaleScenario/busFare.xml", busFareGetter);
			controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
					"fare/GMB.csv", "fare/light_rail_fares.csv"));
			
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		controler.addOverridingModule(new SignalsModule());
		controler.addOverridingModule(new AnaModelCalibrationModule(storage, sue,"LargeScaleOutput/Calibration/",params,true));
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();
		
		
	}
}
