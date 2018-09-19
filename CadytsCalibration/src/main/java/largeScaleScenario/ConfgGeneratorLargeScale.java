package largeScaleScenario;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.LinkDynamics;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;

public class ConfgGeneratorLargeScale {
	public static void main(String[] args) {
		
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";

		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		
		
		
		
		
		Config config=ConfigUtils.createConfig();
		config.plans().setInputFile("data/populationHKI.xml");
		config.plans().setInputPersonAttributeFile("data/personAttributesHKI.xml");
		config.network().setInputFile("data/network.xml");
		config.network().setLaneDefinitionsFile("data/lane_definitions_v2.0.xml");
		config.transit().setTransitScheduleFile("data/transitSchedule.xml");
		config.transit().setVehiclesFile("data/transitVehicles.xml");
		config.vehicles().setVehiclesFile("data/VehiclesHKI.xml");
		config.qsim().setUsingFastCapacityUpdate(false);
		
		SignalSystemsConfigGroup cs=new SignalSystemsConfigGroup();
		cs.setSignalControlFile("data/signal_control.xml");
		cs.setSignalGroupsFile("data/signal_groups.xml");
		cs.setSignalSystemFile("data/signal_systems.xml");
		cs.setUseSignalSystems(true);
		
		config.addModule(cs);
		
		
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
		
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.qsim().setNumberOfThreads(16);
		config.qsim().setStorageCapFactor(2);
		config.qsim().setFlowCapFactor(1.0);
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
		
		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		config.controler().setWriteEventsInterval(50);
		config.qsim().setUseLanes(true);
		config.qsim().setLinkDynamics(LinkDynamics.PassingQ);
		config.qsim().setStartTime(0.0);
		config.qsim().setEndTime(28*3600);
		config.qsim().setStuckTime(1000);
		config.qsim().setNodeOffset(20.);
		HashSet<String> modes=new HashSet<>();
		modes.add("pt");
		modes.add("train");
		modes.add("tram");
		config.transit().setTransitModes(modes);
		config.controler().setLinkToLinkRoutingEnabled(true);
		config.transit().setUseTransit(true);
		config.transitRouter().setAdditionalTransferTime(2.0);
		config.transitRouter().setDirectWalkFactor(100.);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(200.);
		config.transitRouter().setExtensionRadius(200);
		config.transitRouter().setSearchRadius(500);
		config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(true);
		//saveNetworkTransitAndVehicleInformation("data/LargeScaleScenario/modeDetails.txt",config);
		ParamReader pReader=new ParamReader("input/subPopParamAndLimit.csv");
		
		pReader.SetParamToConfig(config, pReader.getInitialParam());
		
		
		new ConfigWriter(config).write("data/configFinal.xml");
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
