package cadytsTry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import cadyts.measurements.SingleLinkMeasurement.TYPE;
import cadyts.supply.SimResults;

public class SimPCUResults<Lint> implements SimResults<Link>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final PCUVolumeAnalyzer volumesAnalyzer;
	private final double countsScaleFactor;
	
	
	public SimPCUResults( final PCUVolumeAnalyzer volumesAnalyzer, final double countsScaleFactor) {
		this.volumesAnalyzer = volumesAnalyzer;
		this.countsScaleFactor = countsScaleFactor;
	}
	
	@Override
	public double getSimValue(final Link link, final int startTime_s, final int endTime_s, final TYPE type) { // stopFacility or link

		Id<Link> linkId = link.getId();
		double[] values = volumesAnalyzer.getPCUVolumesPerHourForLink(linkId);
		
		if (values == null) {
			return 0;
		}
		
		int startHour = startTime_s / 3600;
		int endHour = (endTime_s-3599)/3600 ;
		// (The javadoc specifies that endTime_s should be _exclusive_.  However, in practice I find 7199 instead of 7200.  So
		// we are giving it an extra second, which should not do any damage if it is not used.) 
		if (endHour < startHour) {
			System.err.println(" startTime_s: " + startTime_s + "; endTime_s: " + endTime_s + "; startHour: " + startHour + "; endHour: " + endHour );
			throw new RuntimeException("this should not happen; check code") ;
		}
		double sum = 0. ;
		for ( int ii=startHour; ii<=endHour; ii++ ) {
			sum += values[ii] ;
		}
		switch(type){
		case COUNT_VEH:
			return sum * this.countsScaleFactor ;
		case FLOW_VEH_H:
			return 3600*sum / (endTime_s - startTime_s) * this.countsScaleFactor ;
		default:
			throw new RuntimeException("count type not implemented") ;
		}

	}
	
	
	public double getHourlySimValue(final Id<Link> linkId, final int hour, final TYPE type) { // stopFacility or link

		double[] values = volumesAnalyzer.getPCUVolumesPerHourForLink(linkId);
		
		if (values == null) {
			return 0;
		}
		
		int startHour = hour-1;
		int endHour =hour ;
		// (The javadoc specifies that endTime_s should be _exclusive_.  However, in practice I find 7199 instead of 7200.  So
		// we are giving it an extra second, which should not do any damage if it is not used.) 
		if (endHour < startHour) {
			System.err.println("startHour: " + startHour + "; endHour: " + endHour );
			throw new RuntimeException("this should not happen; check code") ;
		}
		double sum = 0. ;
		for ( int ii=startHour; ii<=endHour; ii++ ) {
			sum += values[ii] ;
		}
		switch(type){
		case COUNT_VEH:
			return sum * this.countsScaleFactor ;
		case FLOW_VEH_H:
			return 3600*sum / (endHour*3600 - startHour*3600) * this.countsScaleFactor ;
		default:
			throw new RuntimeException("count type not implemented") ;
		}

	}

	@Override
	public String toString() {
		final StringBuffer stringBuffer2 = new StringBuffer();
		final String LINKID = "linkId: ";
		final String VALUES = "; values:";
		final char TAB = '\t';
		final char RETURN = '\n';

		for (Id linkId : this.volumesAnalyzer.getLinkIds()) { // Only occupancy!
			StringBuffer stringBuffer = new StringBuffer();
			stringBuffer.append(LINKID);
			stringBuffer.append(linkId);
			stringBuffer.append(VALUES);

			boolean hasValues = false; // only prints stops with volumes > 0
			double[] values = this.volumesAnalyzer.getPCUVolumesForLink(linkId);

			for (int ii = 0; ii < values.length; ii++) {
				hasValues = hasValues || (values[ii] > 0);

				stringBuffer.append(TAB);
				stringBuffer.append(values[ii]);
			}
			stringBuffer.append(RETURN);
			if (hasValues) stringBuffer2.append(stringBuffer.toString());
		}
		return stringBuffer2.toString();
	}

}
