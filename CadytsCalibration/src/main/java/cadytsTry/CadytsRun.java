package cadytsTry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.contrib.signals.builder.SignalsModule;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;

public class CadytsRun {
	//@SuppressWarnings("deprecation")
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
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
		config.plans().setInputFile("data/populationHKI.xml");
		//config.plans().setInputFile("data/output_plans.xml.gz");
		//config.plans().setInputFile("data/populationHKI.xml"); 
		config.plans().setInputPersonAttributeFile("data/personAttributesHKI.xml");
		config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME"); /* This is the default anyway. */
		config.vehicles().setVehiclesFile("data/VehiclesHKI.xml");
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.qsim().setNumberOfThreads(20);
		config.qsim().setStorageCapFactor(2);
		config.qsim().setFlowCapFactor(1.0);
		config.global().setNumberOfThreads(20);
		config.parallelEventHandling().setNumberOfThreads(7);
		config.parallelEventHandling().setEstimatedNumberOfEvents((long) 1000000000);
        config.counts().setAverageCountsOverIterations(1);
        config.counts().setWriteCountsInterval(1);
       
        
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
		
		config.addModule(cadytsConfig);
		
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
		controler.addOverridingModule(new SignalsModule());
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get()));
		controler.addOverridingModule(new CadytsModule());
		controler.getConfig().controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		
		//add Cadyts Scoring
		controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
			@Inject CadytsContextHKI cadytsContextHKI;
			@Inject ScoringParametersForPerson parameters;
			@Override
			public ScoringFunction createNewScoringFunction(Person person) {
				final ScoringParameters params = parameters.getScoringParameters(person);

				SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelLegScoring(params, controler.getScenario().getNetwork()));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params)) ;
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));

				final CadytsScoring<Link> scoringFunction = new CadytsScoring<>(person.getSelectedPlan(), config, cadytsContextHKI);
				scoringFunction.setWeightOfCadytsCorrection(30. * config.planCalcScore().getBrainExpBeta()) ;
				scoringFunctionAccumulator.addScoringFunction(scoringFunction );

				return scoringFunctionAccumulator;
			}
		}) ;
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


	private static double changeCount = 0;
	public static void changeAllModeToPt(Scenario scenario) {
		for(Iterator<?> it = scenario.getPopulation().getPersons().entrySet().iterator(); it.hasNext(); ){
			Map.Entry<?, ?> entry = (Entry<?, ?>) it.next();
			Person person = (Person) entry.getValue();
			if(person.getId().toString().matches("\\d+.0_\\d+.0_\\d+")) {
				for(PlanElement pe: person.getSelectedPlan().getPlanElements()){
					if(pe instanceof Leg){
						if( !((Leg) pe).getMode().equals("pt")){
							((Leg) pe).setMode("pt");
							changeCount++;
						}
					}
				}
			}
		}
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


	private static void saveNetworkTransitAndVehicleInformation(String fileLoc, Config config) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc));
			Scenario scenario=ScenarioUtils.loadScenario(config);
			Network net=scenario.getNetwork();
			Vehicles v=scenario.getVehicles();
			Vehicles trv=scenario.getTransitVehicles();
			TransitSchedule ts=scenario.getTransitSchedule();

			fw.append("Total Link = "+net.getLinks().size());
			fw.append("Total Nodes  = "+net.getNodes().size());
			fw.append("Total Vehicles = "+v.getVehicles().size());
			fw.append("Total TransitVehicles = "+trv.getVehicles().size());
			fw.append("Total TransitLine = "+ts.getTransitLines().size());
			double tsRoute=0;
			double busLine=0;
			double trainLine=0;
			double shipLine=0;
			double tramLine=0;
			for(TransitLine tl:ts.getTransitLines().values()) {
				tsRoute+=tl.getRoutes().size();
				for(TransitRoute tr:tl.getRoutes().values()) {
					if(tr.getTransportMode().equals("bus")) {
						busLine++;
					}else if(tr.getTransportMode().equals("train")) {
						trainLine++;
					}else if(tr.getTransportMode().equals("tram")) {
						tramLine++;
					}else if(tr.getTransportMode().equals("ship")) {
						shipLine++;
					}
					break;
				}
			}
			fw.append("Total TransitRoute = "+tsRoute);
			fw.append("No of Busline = "+busLine);
			fw.append("No of TrainLine = "+trainLine);
			fw.append("no of shipLine = "+shipLine);
			fw.append("no Of tramLine = "+tramLine);
			fw.append("Total Population = "+scenario.getPopulation().getPersons().size());
			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
