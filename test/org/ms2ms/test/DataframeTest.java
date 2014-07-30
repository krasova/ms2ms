package org.ms2ms.test;

import org.junit.Before;
import org.junit.Test;
import org.ms2ms.alg.Dataframes;
import org.ms2ms.data.Dataframe;
import org.ms2ms.utils.Stats;

import java.io.IOException;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: wyu
 * Date: 7/17/14
 * Time: 6:42 AM
 * To change this template use File | Settings | File Templates.
 */
public class DataframeTest extends TestAbstract
{
  String root = "/media/data/maxquant/20081129_Orbi6_NaNa_SA_FASP_out/combined/txt/";
  Dataframe evidences = null;

  @Before
  public void setUp()
  {
    evidences = new Dataframe(root+"evidence1k.txt", '\t');
  }
  @Test
  public void pivoting() throws Exception
  {
    // Dataframe pivot(String col, String val, Stats.Aggregator func, String... rows)
    Dataframe out = evidences.pivot("Raw file", "Retention time calibration", Stats.Aggregator.COUNT, "Modified sequence");
    System.out.println("\n" + out.display());
  }
  @Test
  public void splitting() throws Exception
  {
    // Dataframe pivot(String col, String val, Stats.Aggregator func, String... rows)
    Map<Object, Dataframe> outs = evidences.split("Raw file");

    for (Object obj : outs.keySet())
      System.out.println(obj.toString() + "\n" + outs.get(obj).display());
  }
  @Test
  public void interpolating() throws Exception
  {
    // Dataframe pivot(String col, String val, Stats.Aggregator func, String... rows)
    double xs[] = evidences.getDoubleCol("Retention time");
    double ys[] = evidences.getDoubleCol("Retention time calibration");
    double Xs[] = evidences.getDoubleCol("Calibrated retention time start");

    double[] Ys = Stats.interpolate(xs, ys, 0.3, Xs);

    evidences.addVar("interpolated", Ys);
    System.out.println("\n" + evidences.display());

    evidences.addVar("Calibrated RT", Stats.sum(evidences.getDoubleCol("Retention time"), evidences.getDoubleCol("Retention time calibration")));
  }
}
