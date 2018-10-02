package SimRun;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.builder.SignalsModule;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;
import org.matsim.core.config.groups.QSimConfigGroup.VehiclesSource;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParser;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.AnaModelCalibrationModule;

/**
 * This is the normal simulation run
 * @author cetest
 *
 */
public class SimRun {
	public static void main(String[] args) {
		Config config= ConfigUtils.createConfig();
		
		ConfigUtils.loadConfig(config, "data/configFinal.xml");
		config.controler().setLastIteration(400);
		config.controler().setOutputDirectory("output_warmStart");
		config.facilities().setAssigningOpeningTime(false);
		config.facilities().setAddEmptyActivityOption(false);
		config.facilities().addParam("assigningLinksToFacilitiesIfMissing", "true");
		config.facilities().addParam("oneFacilityPerLink", "true");
		config.facilities().addParam("removingLinksAndCoordinates", "true");
		config.qsim().setVehiclesSource(VehiclesSource.fromVehiclesData);
		config.qsim().setSnapshotStyle(SnapshotStyle.queue);
		config.transitRouter().setDirectWalkFactor(1000);
		config.qsim().addParam("creatingVehiclesForAllNetworkModes", "true");
		config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME");
		String[] modes1=new String[1];
		modes1[0]="car";
		config.subtourModeChoice().setChainBasedModes(modes1);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser;
		
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("data/LargeScaleScenario/busFare.xml", busFareGetter);
			controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(),"data/LargeScaleScenario/mtr_lines_fares.csv","data/LargeScaleScenario/GMB.csv"));
			
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
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();
	}
}
