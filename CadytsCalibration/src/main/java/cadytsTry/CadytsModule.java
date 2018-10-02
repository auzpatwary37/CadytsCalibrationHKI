package cadytsTry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.cadyts.car.CadytsContext;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.CountsConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class CadytsModule extends AbstractModule{

	private final Counts<Link> calibrationCounts;
	private Measurements calibrationMeasurement;
	
	public CadytsModule() {
		this.calibrationCounts = null;
	}

	public CadytsModule(Counts<Link> calibrationCounts) {
		this.calibrationCounts = calibrationCounts;
	}
	
	@Override
	public void install() {
		if (calibrationCounts != null) {
			bind(Key.get(new TypeLiteral<Counts<Link>>(){}, Names.named("calibration"))).toInstance(calibrationCounts);
		} else {
			bind(Key.get(new TypeLiteral<Counts<Link>>(){}, Names.named("calibration"))).toProvider(CalibrationCountsProvider.class).in(Singleton.class);
		}
		
		bind(Measurements.class).annotatedWith(Names.named("CalibrationCounts")).toProvider(MeasurementsProvider.class).in(Singleton.class);
		
		bind(VolumesAnalyzer.class).to(PCUVolumeAnalyzer.class);
		// In principle this is bind(Counts<Link>).to...  But it wants to keep the option of multiple counts, under different names, open.
		// I think.  kai, jan'16
		
		bind(CadytsContextHKI.class).asEagerSingleton();
		addControlerListenerBinding().to(CadytsContextHKI.class);
		//bind(CadytsContextHKI.class).toInstance(new CadytsContextHKI(config, null, calibrationCounts, null, null, null));
	}

	private static Measurements generateMeasurementsFromCount(Counts<Link> counts) {
		Map<String,Tuple<Double,Double>> timeId=new HashMap<>();
		
		for(Count<Link> c:counts.getCounts().values()) {
			for(int hour:c.getVolumes().keySet()) {
				if(!timeId.containsKey(Integer.toString(hour))){
					timeId.put(Integer.toString(hour), new Tuple<Double,Double>((hour-1)*3600.,hour*3600.));
				}
			}
			
		}
		Measurements m=Measurements.createMeasurements(timeId);
		
		for(Count<Link> c:counts.getCounts().values()) {
			m.createAnadAddMeasurement(c.getId().toString());
			Measurement mm=m.getMeasurements().get(Id.create(c.getId().toString(), Measurement.class));
			ArrayList<Id<Link>> linkList=new ArrayList<Id<Link>>();
			linkList.add(c.getId());
			mm.setAttribute(mm.linkListAttributeName,linkList);
			for(int hour:c.getVolumes().keySet()) {
				mm.addVolume(Integer.toString(hour), c.getVolume(hour).getValue());
			}
		}
		return m;
	}
	
	private static class CalibrationCountsProvider implements Provider<Counts<Link>> {
		@Inject CountsConfigGroup config;
		@Inject Config matsimConfig;
		@Override
		public Counts<Link> get() {
			Counts<Link> calibrationCounts = new Counts<>();
			String CountsFilename = config.getCountsFileURL(matsimConfig.getContext()).getFile();
			new MatsimCountsReader(calibrationCounts).readFile(CountsFilename);
	
			return calibrationCounts;
			
		}
	}
	private static class MeasurementsProvider implements Provider<Measurements> {
		@Inject @Named("calibration") Counts<Link> calibrationCounts;
		
		public Measurements get() {
	
			return generateMeasurementsFromCount(calibrationCounts);
			
		}
	}
}