package cadytsTry;

import java.util.HashMap;
import java.util.Map;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

/**
 * Wrapper class around VolumeAnalyzer for PCU measurement
 *
 */

public class PCUVolumeAnalyzer extends VolumesAnalyzer implements VehicleEntersTrafficEventHandler, LinkEnterEventHandler,TransitDriverStartsEventHandler{
	@Inject
	private Scenario scenario;
	
	private final int timeBinSize;
	private final int maxTime;
	private final int maxSlotIndex;
	private Map<Id<Link>, int[]> PCUlinks;
	private HashMap<Id<Vehicle>,Double> enRoutePcu=new HashMap<>();
	
	@Inject
	PCUVolumeAnalyzer(Network network, EventsManager eventsManager) {
		super(3600, 24 * 3600 - 1, network);
		this.PCUlinks = new HashMap<>((int) (network.getLinks().size() * 1.1), 0.95f);
		eventsManager.addHandler(this);
		this.timeBinSize = 3600;
		this.maxTime = 24 * 3600 - 1;
		this.maxSlotIndex = (this.maxTime/this.timeBinSize) + 1;
	}
	
	@Override	
	public void handleEvent(final VehicleEntersTrafficEvent event) {
		if(this.scenario.getVehicles().getVehicles().get(event.getVehicleId())!=null) {
			this.enRoutePcu.put(event.getVehicleId(), this.scenario.getVehicles().getVehicles().get(event.getVehicleId()).getType().getPcuEquivalents());
		}
	}
	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		this.enRoutePcu.put(event.getVehicleId(), this.scenario.getTransitVehicles().getVehicles().get(event.getVehicleId()).getType().getPcuEquivalents());
	}
	
	//vehicles are counted if they just enter the link at that specific time step
	@Override
	public void handleEvent(final LinkEnterEvent event) {
		int[] volumes = this.PCUlinks.get(event.getLinkId());
		if (volumes == null) {
			volumes = new int[this.maxSlotIndex + 1]; // initialized to 0 by default, according to JVM specs
			this.PCUlinks.put(event.getLinkId(), volumes);
		}
		int timeslot = this.getTimeSlotIndex(event.getTime());
		if(this.enRoutePcu.containsKey(event.getVehicleId())) {
			double pcu=this.enRoutePcu.get(event.getVehicleId());
			
			volumes[timeslot]=volumes[timeslot]+(int)pcu;
		}
		
		//super.handleEvent(event);
}
	
	
	private int getTimeSlotIndex(final double time) {
		if (time > this.maxTime) {
			return this.maxSlotIndex;
		}
		return ((int)time / this.timeBinSize);
	}
	
	public double[] getPCUVolumesPerHourForLink(final Id<Link> linkId) {
		
		double[] volumes = new double[24];
		
		int[] pcuvolumesForLink = this.getPCUVolumesForLink(linkId);
		if (pcuvolumesForLink == null) return volumes;

		int slotsPerHour = (int)(3600.0 / this.timeBinSize);
		for (int hour = 0; hour < 24; hour++) {
			double time = hour * 3600.0;
			for (int i = 0; i < slotsPerHour; i++) {
				volumes[hour] += pcuvolumesForLink[this.getTimeSlotIndex(time)];
				time += this.timeBinSize;
			}
		}
		return volumes;
	}
@Override
public double[] getVolumesPerHourForLink(final Id<Link> linkId) {
		
		double[] volumes = new double[24];
		
		int[] pcuvolumesForLink = this.getPCUVolumesForLink(linkId);
		if (pcuvolumesForLink == null) return volumes;

		int slotsPerHour = (int)(3600.0 / this.timeBinSize);
		for (int hour = 0; hour < 24; hour++) {
			double time = hour * 3600.0;
			for (int i = 0; i < slotsPerHour; i++) {
				volumes[hour] += pcuvolumesForLink[this.getTimeSlotIndex(time)];
				time += this.timeBinSize;
			}
		}
		return volumes;
	}
	
	public int[] getPCUVolumesForLink(final Id<Link> linkId) {	
		return this.PCUlinks.get(linkId);
	}
	
	@Override
	public int[] getVolumesForLink(final Id<Link> linkId) {	
		return this.PCUlinks.get(linkId);
	}
	
	@Override
	public void reset(final int iteration) {
		this.PCUlinks.clear();
		this.enRoutePcu.clear();
		super.reset(iteration);
	}

	
}

	