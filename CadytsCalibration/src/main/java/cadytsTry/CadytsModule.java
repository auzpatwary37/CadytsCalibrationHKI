package cadytsTry;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.cadyts.car.CadytsContext;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.CountsConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.counts.Counts;
import org.matsim.counts.MatsimCountsReader;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class CadytsModule extends AbstractModule{

	private final Counts<Link> calibrationCounts;

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
		bind(VolumesAnalyzer.class).to(PCUVolumeAnalyzer.class);
		// In principle this is bind(Counts<Link>).to...  But it wants to keep the option of multiple counts, under different names, open.
		// I think.  kai, jan'16
		
		bind(CadytsContextHKI.class).asEagerSingleton();
		addControlerListenerBinding().to(CadytsContextHKI.class);
		//bind(CadytsContextHKI.class).toInstance(new CadytsContextHKI(config, null, calibrationCounts, null, null, null));
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
}