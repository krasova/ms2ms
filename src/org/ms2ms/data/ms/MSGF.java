package org.ms2ms.data.ms;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.TreeMultimap;
import org.ms2ms.r.Dataframe;
import org.ms2ms.utils.Strs;
import org.ms2ms.utils.TabFile;
import org.ms2ms.utils.Tools;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by yuw on 5/18/17.
 */
public class MSGF
{
  public static Table<String, String, Map<String, String>> parseMSGF(String search) throws IOException
  {
    Table<String, String, Map<String, String>> out = HashBasedTable.create();

    TabFile  msgf = new TabFile(search, TabFile.tabb);
    while(msgf.hasNext())
    {
      String  row = "#"+msgf.get("ScanNum"),
          peptide = msgf.get("Peptide").replaceAll("[0|1|2|3|4|5|6|7|8|9|\\\\+|\\\\.|\\\\-]", "");

      Map<String, String> props = out.get(row, peptide);
      if (props==null || Tools.getDouble(props, "MSGFScore")>msgf.getDouble("MSGFScore"))
        out.put(row, peptide, msgf.getMappedRow());
    }
    msgf.close();

    return out;
  }
  public static void parseMSGFSearches(String search, String assignments) throws IOException
  {
    Map<String, String> runscan_seq = new HashMap<>();

    if (Strs.isSet(assignments))
    {
      TabFile refs = new TabFile(assignments, TabFile.comma);
      while (refs.hasNext())
      {
        if (refs.get("Protein")==null || refs.get("Protein").indexOf("XXX")<0)
          runscan_seq.put(refs.get("Run")+"#"+refs.getInt("Scan"), refs.get("Sequence")+" m/z"+refs.get("mz") + "@z"+refs.get("z"));
      }
      refs.close();
    }

    TreeMultimap<Integer, Double> all = TreeMultimap.create(), annot = TreeMultimap.create();
    Set<String> distincts = new HashSet<>();

    Table<String, String, Map<String, String>> psm = parseMSGF(search);

    int passed=0, totals=0, ids=0, specs=0, good_miss=0;
    Dataframe out = new Dataframe("");
    for (Map<String, String> props : psm.values())
    {
      totals++;

      double     fdr = Tools.getDouble(props, "QValue");
      int        row = Tools.getInt(props, "ScanNum");
      String peptide = props.get("Peptide");
      String[] scans = Strs.split(props.get("Title"), '+');

      specs += scans.length;

      Set<String> scns = new HashSet<>();
      for (String scan : scans)
      {
        scns.add(Strs.split(scan, "m", true).get(0));
      }
      all.put(scans.length, fdr);
      if (fdr<=0.01)
      {
        passed++; ids += scans.length;

        annot.put(scans.length, fdr);
        distincts.add(peptide.replaceAll("[0|1|2|3|4|5|6|7|8|9|\\\\+|\\\\.|\\\\-]", ""));

        out.put(row, "Charge",       Tools.getInt(props, "Charge"));
        out.put(row, "DeNovoScore",  Tools.getDouble(props,"DeNovoScore"));
        out.put(row, "IsotopeError", Tools.getInt(props,"IsotopeError"));
        out.put(row, "MSGFScore",    Tools.getDouble(props,"MSGFScore"));
        out.put(row, "PepQValue",    Tools.getDouble(props,"PepQValue"));
        out.put(row, "m.z",          Tools.getDouble(props,"Precursor"));
        out.put(row, "ppm",          Tools.getDouble(props,"PrecursorError(ppm)"));
        out.put(row, "QValue",       Tools.getDouble(props,"QValue"));
        out.put(row, "SpecEValue",   Tools.getDouble(props,"SpecEValue"));
        out.put(row, "Peptide",      props.get("Peptide"));
        out.put(row, "Protein",      props.get("Protein"));
      }
      else if (Sets.intersection(scns, runscan_seq.keySet()).size()>0)
      {
        good_miss++;
        if (scans.length>2)
        {
          System.out.println(row+", "+peptide+"@"+props.get("QValue")+" m/z"+Tools.d2s(Tools.getDouble(props, "Precursor"), 4)+" @z"+Tools.getInt(props, "Charge"));
          for (String scn : scns)
            System.out.println("    "+scn+"\t"+runscan_seq.get(scn));
        }
      }
    }
    out.init(false);
    out.write(new FileWriter("/Users/yuw/Apps/pipeline/Chorus/data/cKit/NR/NR_20170513_qual.tsv"), "\t");

    int annotated=0, alls=0;
    for (Integer cnt : annot.keySet()) annotated+=cnt*annot.get(cnt).size();
    for (Integer cnt :   all.keySet())      alls+=cnt*  all.get(cnt).size();

    System.out.println("Annot/All: " + annotated+"/"+alls + "--> " + Tools.d2s(100d*annotated/alls, 2));
  }
}
