package cadytsTry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.log4j.Logger;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.cadyts.car.CadytsContext;

import org.matsim.contrib.cadyts.general.CadytsBuilderImpl;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.CadytsContextI;
import org.matsim.contrib.cadyts.general.CadytsCostOffsetsXMLFileIO;
import org.matsim.contrib.cadyts.general.PlansTranslator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.counts.Counts;

import cadyts.calibrators.analytical.AnalyticalCalibrator;
import cadyts.measurements.SingleLinkMeasurement.TYPE;
import cadyts.supply.SimResults;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class CadytsContextHKI implements CadytsContextI<Link>, StartupListener, IterationEndsListener, BeforeMobsimListener{
	private final static Logger log = Logger.getLogger(CadytsContext.class);

	private final static String LINKOFFSET_FILENAME = "linkCostOffsets.xml";
	private static final String FLOWANALYSIS_FILENAME = "flowAnalysis.txt";
	
	private final double countsScaleFactor;
	private final Counts<Link> calibrationCounts;
	private final boolean writeAnalysisFile;

	private AnalyticalCalibrator<Link> calibrator;
	
	private SimResults<Link> simResults;
	private Scenario scenario;
	private EventsManager eventsManager;
	private VolumesAnalyzer volumesAnalyzer;
	private OutputDirectoryHierarchy controlerIO;
	@Inject
	private @Named("CalibrationCounts")Measurements calibrationMeasurements;

	private PlansTranslatorBasedOnEvents plansTranslator;

	@Inject
	CadytsContextHKI(Config config, Scenario scenario, @Named("calibration") Counts<Link> calibrationCounts, EventsManager eventsManager, VolumesAnalyzer volumesAnalyzer, OutputDirectoryHierarchy controlerIO) {
		this.scenario = scenario;
		this.calibrationCounts = calibrationCounts;
		this.eventsManager = eventsManager;
		this.volumesAnalyzer = volumesAnalyzer;
		this.controlerIO = controlerIO;
		this.countsScaleFactor = config.counts().getCountsScaleFactor();

		CadytsConfigGroup cadytsConfig = ConfigUtils.addOrGetModule(config, CadytsConfigGroup.GROUP_NAME, CadytsConfigGroup.class);
		// addModule() also initializes the config group with the values read from the config file
		cadytsConfig.setWriteAnalysisFile(true);


		Set<String> countedLinks = new TreeSet<>();
		for (Id<Link> id : this.calibrationCounts.getCounts().keySet()) {
			countedLinks.add(id.toString());
		}

		cadytsConfig.setCalibratedItems(countedLinks);

		this.writeAnalysisFile = cadytsConfig.isWriteAnalysisFile();
	}

	@Override
	public PlansTranslator<Link> getPlansTranslator() {
		return this.plansTranslator;
	}
	
	@SuppressWarnings({ "static-access", "rawtypes", "unchecked" })
	@Override
	public void notifyStartup(StartupEvent event) {
		this.simResults = new SimPCUResults((PCUVolumeAnalyzer) volumesAnalyzer, this.countsScaleFactor);
		
		// this collects events and generates cadyts plans from it
		this.plansTranslator = new PlansTranslatorBasedOnEvents(scenario);
		this.eventsManager.addHandler(plansTranslator);

		this.calibrator = new CadytsBuilderImpl().buildCalibratorAndAddMeasurements(scenario.getConfig(), this.calibrationCounts , new LinkLookUp(scenario) /*, cadytsConfig.getTimeBinSize()*/, Link.class);
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		// Register demand for this iteration with Cadyts.
		// Note that planToPlanStep will return null for plans which have never been executed.
		// This is fine, since the number of these plans will go to zero in normal simulations,
		// and Cadyts can handle this "noise". Checked this with Gunnar.
		// mz 2015
		for (Person person : scenario.getPopulation().getPersons().values()) {
			this.calibrator.addToDemand(plansTranslator.getCadytsPlan(person.getSelectedPlan()));
		}
	}

	@Override
	public void notifyIterationEnds(final IterationEndsEvent event) {
		if (this.writeAnalysisFile) {
			String analysisFilepath = null;
			if (isActiveInThisIteration(event.getIteration(), scenario.getConfig())) {
				analysisFilepath = controlerIO.getIterationFilename(event.getIteration(), FLOWANALYSIS_FILENAME);
			}
			this.calibrator.setFlowAnalysisFile(analysisFilepath);
		}
//		try {
//			FileWriter fw=new FileWriter(new File("output_CadytsTry/simResults"+event.getIteration()+".csv"));
//			fw.write("StationId,LinkId,Hour,RealCount,SimCount\n");
//			SimPCUResults<Link> s=(SimPCUResults<Link>)this.simResults;
//			for(Id<Link>linkId:this.calibrationCounts.getCounts().keySet()) {
//				for(int i:this.calibrationCounts.getCount(linkId).getVolumes().keySet()) {
//					fw.write(this.calibrationCounts.getCount(linkId).getCsLabel()+","+linkId+","+i+","+this.calibrationCounts.getCount(linkId).getVolume(i)+","+s.getHourlySimValue(linkId,i, TYPE.COUNT_VEH)+"\n");
//				}
//			}
//			fw.flush();
//			fw.close();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		this.calibrator.afterNetworkLoading(this.simResults);
		
		//Create Measurements
		Measurements m=this.calibrationMeasurements.clone();
		Network net=event.getServices().getScenario().getNetwork();
		for(Measurement mm:this.calibrationMeasurements.getMeasurements().values()) {
			for(String timeId:mm.getVolumes().keySet()) {
				Link l=net.getLinks().get(Id.createLinkId(mm.getId().toString()));
				m.getMeasurements().get(mm.getId()).addVolume(timeId, this.simResults.getSimValue(l, this.calibrationMeasurements.getTimeBean().get(timeId).getFirst().intValue(), this.calibrationMeasurements.getTimeBean().get(timeId).getSecond().intValue(),TYPE.COUNT_VEH));
			}
			
		}
		
		String filenameM = controlerIO.getIterationFilename(event.getIteration(), "Measurements_Compariosn.csv");
		writeMeasurementsComparison(filenameM,this.calibrationMeasurements,m);
		//---------------------------------------------------------------------------------------

		// write some output
		String filename = controlerIO.getIterationFilename(event.getIteration(), LINKOFFSET_FILENAME);
		try {
			new CadytsCostOffsetsXMLFileIO<>(new LinkLookUp(scenario), Link.class)
   			   .write(filename, this.calibrator.getLinkCostOffsets());
		} catch (IOException e) {
			log.error("Could not write link cost offsets!", e);
		}
		this.volumesAnalyzer.reset(event.getIteration());
	}

	/**
	 * for testing purposes only
	 */
	@Override
	public AnalyticalCalibrator<Link> getCalibrator() {
		return this.calibrator;
	}

	// ===========================================================================================================================
	// private methods & pure delegate methods only below this line

	private static boolean isActiveInThisIteration(final int iter, final Config config) {
		return (iter > 0 && iter % config.counts().getWriteCountsInterval() == 0);
	}
	
	public static void writeMeasurementsComparison(String fileLoc,Measurements realMeasurements,Measurements simMeasurements) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc),false);
			fw.append("MeasurementId,timeBeanId,RealCount,currentSimCount\n");
			for(Measurement m: realMeasurements.getMeasurements().values()) {
				for(String timeBean:m.getVolumes().keySet()) {
					
					fw.append(m.getId()+","+timeBean+","+realMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+","+
				simMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+"\n");
					}
			}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
