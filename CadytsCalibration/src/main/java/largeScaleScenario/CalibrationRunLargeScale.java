package largeScaleScenario;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import org.apache.log4j.PropertyConfigurator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModelSubPop;
import ust.hk.praisehk.metamodelcalibration.calibrator.Calibrator;
import ust.hk.praisehk.metamodelcalibration.calibrator.CalibratorImpl;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.SimAndAnalyticalGradientCalculator;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;

public class CalibrationRunLargeScale {
	public static void main(String[] args) {
		
		PropertyConfigurator.configure("src/main/resources/log4j.properties");
		
		final boolean internalCalibration=false;
		
		
		Measurements calibrationMeasurements=new MeasurementsReader().readMeasurements("data/Output/ATCMeasurementsPeak.xml");
		Config initialConfig=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(initialConfig,"data/LargeScaleScenario/configFinal.xml");
		ParamReader pReader=new ParamReader("data/LargeScaleScenario/subPopParamAndLimit.csv");
		MeasurementsStorage storage=new MeasurementsStorage(calibrationMeasurements);
		LinkedHashMap<String,Double>initialParams=pReader.getInitialParam();
		LinkedHashMap<String,Double>params=initialParams;
		
		Calibrator calibrator=new CalibratorImpl(calibrationMeasurements,"LargeScaleOutput/Calibration/", internalCalibration, pReader,25, 4);
		
		calibrator.setMaxTrRadius(75);
	
		
		SimRun simRun=new SimRunHKI();
		
		writeRunParam(calibrator, "toyScenario/Calibration/", params, pReader);
		AnalyticalModel sue=new CNLSUEModelSubPop(calibrationMeasurements.getTimeBean(),pReader);
		
		for(int i=0;i<50;i++) {
			Config config=pReader.SetParamToConfig(initialConfig, params);
			
			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
			sue.setFileLoc("LargeScaleOutput/");
			simRun.run(sue, config, params, true, Integer.toString(i), storage);
			
			
			//Insert Gradient Calculator
			//SimAndAnalyticalGradientCalculator gradientFactory=new SimAndAnalyticalGradientCalculator(config, storage, simRun, params, calibrator.getTrRadius()/2/100, "FD", i, false, pReader);
			//params=calibrator.generateNewParam(sue, storage.getSimMeasurement(params), gradientFactory.getSimGradient(), gradientFactory.getAnaGradient(), MetaModel.GradientBased_III_MetaModelName);
			
			
			params=calibrator.generateNewParam(sue, storage.getSimMeasurement(params), null, null, MetaModel.AnalyticalLinearMetaModelName);
						
		}
		
		
		
	}
	
	public static void writeRunParam(Calibrator calibrator,String fileWriteLoc,LinkedHashMap<String,Double>params,ParamReader pReader) {
		try {
			FileWriter fw=new FileWriter(new File(fileWriteLoc+"RunParam.csv"),true);
			fw.append("ParameterName,initialParam ,upperLimit,lowerLimit\n");
			for(String s:params.keySet()) {
				fw.append(s+","+params.get(s)+","+pReader.getParamLimit().get(s).getFirst()+","+pReader.getParamLimit().get(s).getSecond()+"\n");
			}
			fw.append("initialTrustRegionRadius,"+calibrator.getTrRadius()+"\n");
			fw.append("TrInc,"+calibrator.getTrusRegionIncreamentRatio()+"\n");
			fw.append("TrDecreasing,"+calibrator.getTrustRegionDecreamentRatio()+"\n");
			fw.append("MaxTr,"+calibrator.getMaxTrRadius()+"\n");
			fw.append("MinTr,"+calibrator.getMinTrRadius()+"\n");
			fw.append("rou,"+calibrator.getThresholdErrorRatio()+"\n");
			fw.append("AnalyticalModelInternalParamterCalibration,"+calibrator.isShouldPerformInternalParamCalibration()+"\n");
			fw.append("MaxSuccesiveRejection,"+calibrator.getMaxSuccesiveRejection()+"\n");
			fw.append("MinMetaChangeReq,"+calibrator.getMinMetaParamChange()+"\n");
			fw.append("ObjectiveType,"+calibrator.getObjectiveType()+"\n");
			
			fw.append("StrtingTime,"+LocalDateTime.now().toString()+"\n");
			
			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
