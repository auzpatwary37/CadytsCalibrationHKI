package SimRun;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;
import org.matsim.core.config.groups.QSimConfigGroup.VehiclesSource;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParser;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.AnaModelCalibrationModule;

/**
 * This is the normal simulation run
 * @author cetest
 *
 */
public class SimRun {
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		Config config=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config_clean.xml");

		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";

		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";

		
		
		Config configGV = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configGV, "data/config_Ashraf.xml");
		for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
			if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
				config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(act);
			}
		}
		
		ParamReader pReader=new ParamReader("input/subPopParamAndLimit.csv");
		
		pReader.SetParamToConfig(config, pReader.getInitialParam());
		
		
		config.removeModule("emissions");
		config.removeModule("roadpricing");
		TransitRouterFareDynamicImpl.distanceFactor = 0.034;
		//config.plans().setInputFile("data/populationHKI.xml");
		config.plans().setInputFile("data/output_plans.xml.gz");
		//config.plans().setInputFile("data/populationHKI.xml"); 
		config.plans().setInputPersonAttributeFile("data/personAttributesHKI.xml");
		config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME"); /* This is the default anyway. */
		config.vehicles().setVehiclesFile("data/VehiclesHKI.xml");
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.qsim().setNumberOfThreads(16);
		config.qsim().setStorageCapFactor(2);
		config.qsim().setFlowCapFactor(1.2);
		config.global().setNumberOfThreads(23);
		config.parallelEventHandling().setNumberOfThreads(7);
		config.parallelEventHandling().setEstimatedNumberOfEvents((long) 1000000000);

		createStrategies(config, PersonChangeWithCar_NAME, 0.02, 0.015, 0.01, 0);
		createStrategies(config, PersonChangeWithoutCar_NAME, 0.02, 0.015, 0.01, 0);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonChangeWithCar_NAME, 
				0.02, 200);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
				0.025, 200);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonChangeWithoutCar_NAME, 
				0.02, 200);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
				0.025, 200);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
				0.03, 200);
		addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
				0.03, 200);

		createStrategies(config, PersonFixed_NAME, 0.03, 0.03, 0, 450);
		createStrategies(config, GVChange_NAME, 0.03, 0.03, 0, 0);
		createStrategies(config, GVFixed_NAME, 0.03, 0.03, 0, 450);

		//Create CadytsConfigGroup with defaultValue of everything
		
		CadytsConfigGroup cadytsConfig=new CadytsConfigGroup();
		cadytsConfig.setEndTime((int)config.qsim().getEndTime());
		cadytsConfig.setFreezeIteration(Integer.MAX_VALUE);
		cadytsConfig.setMinFlowStddev_vehPerHour(25);
		cadytsConfig.setPreparatoryIterations(10);
		cadytsConfig.setRegressionInertia(.95);
		cadytsConfig.setStartTime(0);
		cadytsConfig.setTimeBinSize(3600);
		cadytsConfig.setUseBruteForce(false);
		cadytsConfig.setWriteAnalysisFile(true);
		cadytsConfig.setVarianceScale(1.0);
		
		//add the cadyts config 
		
		//config.addModule(cadytsConfig);
		
		//general Run Configuration
		config.counts().setInputFile("data/ATC2016Counts.xml");
		config.counts().setInputFile("data/ATCCountsPeakHourLink.xml");
		config.controler().setLastIteration(400);
		config.controler().setOutputDirectory("output_CadytsTry");	

		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		
		
		
		//Modify Lane capacity
		
		for(LanesToLinkAssignment l2l:scenario.getLanes().getLanesToLinkAssignments().values()) {
			for(Lane l: l2l.getLanes().values()) {
				l.setCapacityVehiclesPerHour(1800);
			}
		}
		
		
		Controler controler = new Controler(scenario);
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse("data/busFare.xml", busFareGetter);
		// Add the signal module to the controller
		Signals.configure(controler);
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", 
				"fare/GMB.csv", "fare/light_rail_fares.csv"));
		controler.getConfig().controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		
		controler.run();
	}
	
	private static void createStrategies(Config config, String subpopName, double timeMutationWeight, double reRouteWeight, 
			double changeTripModeWeight, int iterToSwitchOffInnovation) {
		if(timeMutationWeight < 0 || reRouteWeight <0 || changeTripModeWeight <0 || iterToSwitchOffInnovation <0) {
			throw new IllegalArgumentException("The parameters can't be less than 0!");
		}

		if(timeMutationWeight>0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator.toString(), subpopName, 
					timeMutationWeight, 0);
		}

		if(reRouteWeight > 0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), subpopName, 
					reRouteWeight, iterToSwitchOffInnovation);
		}

		if(changeTripModeWeight>0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), subpopName, 
					changeTripModeWeight, iterToSwitchOffInnovation);
		}

		addStrategy(config, DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString(), subpopName, 
				1- changeTripModeWeight - timeMutationWeight - reRouteWeight, 0);
	}
	
	private static void addStrategy(Config config, String strategy, String subpopulationName, double weight, int disableAfter) {
		if(weight <=0 || disableAfter <0) {
			throw new IllegalArgumentException("The parameters can't be less than or equal to 0!");
		}
		StrategySettings strategySettings = new StrategySettings() ;
		strategySettings.setStrategyName(strategy);
		strategySettings.setSubpopulation(subpopulationName);
		strategySettings.setWeight(weight);
		if(disableAfter>0) {
			strategySettings.setDisableAfter(disableAfter);
		}
		config.strategy().addStrategySettings(strategySettings);
	}
}