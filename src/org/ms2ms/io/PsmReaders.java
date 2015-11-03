package org.ms2ms.io;

import com.google.common.base.Optional;
import com.google.common.collect.*;
import info.monitorenter.cpdetector.io.FileFilterExtensions;
import org.apache.commons.io.FilenameUtils;
import org.expasy.mzjava.core.mol.NumericMass;
import org.expasy.mzjava.core.ms.spectrum.TimeUnit;
import org.expasy.mzjava.proteomics.io.ms.ident.PSMReaderCallback;
import org.expasy.mzjava.proteomics.mol.Peptide;
import org.expasy.mzjava.proteomics.mol.modification.ModAttachment;
import org.expasy.mzjava.proteomics.mol.modification.Modification;
import org.expasy.mzjava.proteomics.mol.modification.unimod.UnimodManager;
import org.expasy.mzjava.proteomics.mol.modification.unimod.UnimodModificationResolver;
import org.expasy.mzjava.proteomics.ms.ident.PeptideMatch;
import org.expasy.mzjava.proteomics.ms.ident.PeptideProteinMatch;
import org.expasy.mzjava.proteomics.ms.ident.SpectrumIdentifier;
import org.ms2ms.algo.LCMSMS;
import org.ms2ms.algo.PSMs;
import org.ms2ms.math.Stats;
import org.ms2ms.mzjava.MaxQuantReader;
import org.ms2ms.mzjava.NumModMatchResolver;
import org.ms2ms.mzjava.NumModResolver;
import org.ms2ms.mzjava.ProteinPilotReader;
import org.ms2ms.r.Dataframe;
import org.ms2ms.utils.IOs;
import org.ms2ms.utils.Strs;
import org.ms2ms.utils.TabFile;
import org.ms2ms.utils.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Created by yuw on 9/14/2015.
 */
public class PsmReaders
{
  private final Map<SpectrumIdentifier, List<PeptideMatch>> searchResultMap = new HashMap<>();
  private final Multimap<SpectrumIdentifier, PeptideMatch>  mIdMatch = HashMultimap.create();

  PSMReaderCallback insertIdResultCB = new PSMReaderCallback() {
    @Override
    public void resultRead(SpectrumIdentifier identifier, PeptideMatch searchResult) {

      List<PeptideMatch> results;
      if (searchResultMap.containsKey(identifier)) {
        results = searchResultMap.get(identifier);
      } else {
        results = new ArrayList<>();
        searchResultMap.put(identifier, results);
      }

      results.add(searchResult);
    }
  };
  public PSMReaderCallback insertIdMatch = new PSMReaderCallback() {
    @Override
    public void resultRead(SpectrumIdentifier identifier, PeptideMatch searchResult)
    {
      mIdMatch.put(identifier, searchResult);
    }
  };

  public Map<SpectrumIdentifier, List<PeptideMatch>> getResultMap() { return searchResultMap; }

  public void parseMSGFplusMzID(String filename)
  {
    MzIdentMLReader reader = new MzIdentMLReader(new NumModResolver());
//    MzIdentMlReader reader = new MzIdentMlReader(new NumModResolver());

    FileInputStream file = null;
    try
    {
      try
      {
        file = new FileInputStream(new File(filename));
        reader.parse(new FileInputStream(new File(filename)), insertIdResultCB);
      }
      finally
      {
        if (file!=null) file.close();
      }
    }
    catch (FileNotFoundException e1)
    {
      throw new RuntimeException("File not found: " + filename);
    }
    catch (IOException e2)
    {
      throw new RuntimeException("File I/O exception!");
    }
  }
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMascotPD(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.comma);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("Spectrum File"), '.'), scan = file.get("First Scan");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run+"#"+scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("m/z [Da]"));
        id.setPrecursorIntensity(file.getDouble("Intensity"));
        id.setSpectrumFile(run);
        id.addRetentionTime(file.getDouble("RT [min]"), TimeUnit.MINUTE);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = new PeptideMatch(file.get("Sequence").toUpperCase());
      String[] mods = Strs.split(file.get("Modifications"), ';');
      if (Tools.isSet(mods))
        for (String mod : mods)
        {
          String tag = mod.substring(0, mod.indexOf('(')).trim(), m = mod.substring(mod.indexOf('(')+1, mod.indexOf(')'));
          if      (Strs.equals(tag, "N-Term")) match.addModificationMatch(ModAttachment.N_TERM, new Modification(m, new NumericMass(0d)));
          else if (Strs.equals(tag, "C-Term")) match.addModificationMatch(ModAttachment.C_TERM, new Modification(m, new NumericMass(0d)));
          else
          {
            match.addModificationMatch(new Integer(tag.substring(1, tag.length()))-1, new Modification(m, new NumericMass(0d)));
          }
        }
      match.addProteinMatch(new PeptideProteinMatch(file.get("Protein Group Accessions"),
          Optional.of(file.get("Protein Descriptions")), Optional.of("-"), Optional.of("-"), PeptideProteinMatch.HitType.TARGET));

      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      match.setMassDiff(file.getDouble("?M [ppm]")*id.getPrecursorMz().get()*id.getAssumedCharge().get()*1E-6);
      match.setNumMatchedIons(new Integer(matched[0]));
      match.setTotalNumIons(new Integer(matched[1]));
      match.setNumMissedCleavages(file.getInt("# Missed Cleavages"));
      match.setRank(file.getInt("Rank"));
      match.addScore("IonScore", file.getDouble("IonScore"));
      match.addScore("Exp Value", file.getDouble("Exp Value"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }

    return id_match;
  }
  public static Multimap<SpectrumIdentifier, PeptideMatch> readSequestHT(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("Spectrum File"), '.'), scan = file.get("First Scan");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("m/z [Da]"));
//        id.setPrecursorIntensity(file.getDouble("Intensity"));
        id.setSpectrumFile(run);
        id.addRetentionTime(file.getDouble("RT [min]"), TimeUnit.MINUTE);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = new PeptideMatch(file.get("Annotated Sequence").toUpperCase());
      String[]      mods = Strs.split(file.get("Modifications"), ';');
      if (Tools.isSet(mods))
        for (String mod : mods)
        {
          if (!Strs.isSet(mod) || mod.indexOf('(')<0 || mod.indexOf('(')<0) continue;
          String tag = mod.substring(0, mod.indexOf('(')).trim(), m = mod.substring(mod.indexOf('(')+1, mod.indexOf(')'));
          if      (Strs.equals(tag, "N-Term")) match.addModificationMatch(ModAttachment.N_TERM, new Modification(m, new NumericMass(0d)));
          else if (Strs.equals(tag, "C-Term")) match.addModificationMatch(ModAttachment.C_TERM, new Modification(m, new NumericMass(0d)));
          else
          {
            match.addModificationMatch(new Integer(tag.substring(1, tag.length()))-1, new Modification(m, new NumericMass(0d)));
          }
        }
      match.addProteinMatch(new PeptideProteinMatch(file.getNotNull("Master Protein Accessions", "Protein Accessions"),
          Optional.of("-"), Optional.of("-"), Optional.of("-"), PeptideProteinMatch.HitType.TARGET));

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      match.setMassDiff(file.getDouble("DeltaM [ppm]")*id.getPrecursorMz().get()*id.getAssumedCharge().get()*1E-6);
//      match.setNumMatchedIons(new Integer(matched[0]));
//      match.setTotalNumIons(new Integer(matched[1]));
      match.setNumMissedCleavages(file.getInt("# Missed Cleavages"));
      match.setRank(file.getInt("Rank"));
      match.addScore("XCorr", file.getDouble("XCorr"));
      match.addScore("Percolator q-Value", file.getDouble("Percolator q-Value"));
      match.addScore("Percolator PEP", file.getDouble("Percolator PEP"));
      if (file.getDouble("DeltaScore")!=null) match.addScore("DeltaScore", file.getDouble("DeltaScore"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }

    return id_match;
  }
  // TODO to be completed
  //allPeptide//Raw file	Type	Charge	m/z	Mass	Uncalibrated m/z	Resolution	Number of data points	Number of scans	Number of isotopic peaks	PIF	Mass fractional part	Mass deficit	Mass precision [ppm]	Max intensity m/z 0	Retention time	Retention length	Retention length (FWHM)	Min scan number	Max scan number	Identified	MS/MS IDs	Sequence	Length	Modifications	Modified sequence	Proteins	Score	Intensity	Intensities	MS/MS Count	MSMS Scan Numbers	MSMS Isotope Indices
  //evidence//Sequence	Length	Modifications	Modified sequence	Oxidation (M) Probabilities	Oxidation (M) Score Diffs	Acetyl (Protein N-term)	Oxidation (M)	Missed cleavages	Proteins	Leading proteins	Leading razor protein	Type	Raw file	Fraction	Experiment	MS/MS m/z	Charge	m/z	Mass	Resolution	Uncalibrated - Calibrated m/z [ppm]	Uncalibrated - Calibrated m/z [Da]	Mass Error [ppm]	Mass Error [Da]	Uncalibrated Mass Error [ppm]	Uncalibrated Mass Error [Da]	Max intensity m/z 0	Retention time	Retention length	Calibrated retention time	Calibrated retention time start	Calibrated retention time finish	Retention time calibration	Match time difference	Match m/z difference	Match q-value	Match score	Number of data points	Number of scans	Number of isotopic peaks	PIF	Fraction of total spectrum	Base peak fraction	PEP	MS/MS Count	MS/MS Scan Number	Score	Delta score	Combinatorics	Intensity	Reporter intensity 0	Reporter intensity 1	Reporter intensity 2	Reporter intensity 3	Reporter intensity 4	Reporter intensity 5	Reporter intensity 6	Reporter intensity 7	Reporter intensity 8	Reporter intensity 9	Reporter intensity not corrected 0	Reporter intensity not corrected 1	Reporter intensity not corrected 2	Reporter intensity not corrected 3	Reporter intensity not corrected 4	Reporter intensity not corrected 5	Reporter intensity not corrected 6	Reporter intensity not corrected 7	Reporter intensity not corrected 8	Reporter intensity not corrected 9	Reporter intensity count 0	Reporter intensity count 1	Reporter intensity count 2	Reporter intensity count 3	Reporter intensity count 4	Reporter intensity count 5	Reporter intensity count 6	Reporter intensity count 7	Reporter intensity count 8	Reporter intensity count 9	Reporter PIF	Reporter fraction	Reverse	Potential contaminant	id	Protein group IDs	Peptide ID	Mod. peptide ID	MS/MS IDs	Best MS/MS	AIF MS/MS IDs	Oxidation (M) site IDs
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMaxquant(String mq) throws IOException
  {
    UnimodModificationResolver modResolver = new UnimodModificationResolver();
    modResolver.putTranslate("de", "Deamidated");
    modResolver.putTranslate("ox", "Oxidation");
    modResolver.putTranslate("ac", "Acetyl");
    modResolver.putTranslate("gl", "Gln->pyro-Glu");

    MaxQuantReader maxQuantReader = new MaxQuantReader(modResolver);
    PsmReaders                psm = new PsmReaders();
    maxQuantReader.parse(new File(mq), psm.insertIdMatch);

    System.out.println(psm.mIdMatch.size());

    return psm.mIdMatch;
  }
/*
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMaxquant(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("Raw file"), '.'), scan = file.get("MS/MS Scan Number");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("m/z"));
        id.setPrecursorNeutralMass(file.getDouble("Mass"));
//        id.setPrecursorIntensity(file.getDouble("Intensity"));
        id.setSpectrumFile(run);
        id.addRetentionTime(file.getDouble("Retention time"), TimeUnit.MINUTE);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = new PeptideMatch(file.get("Sequence").toUpperCase());
      String[]      mods = Strs.split(file.get("Modifications"), ';');

//      Oxidation (M)                         2 Oxidation (M)                       Acetyl (Protein N-term),Oxidation (M)
//      Acetyl (Protein N-term)               Gln->pyro-Glu                         Oxidation (M),Gln->pyro-Glu
//      3 Oxidation (M)
      // TODO to work out the mod parsing code
      if (Tools.isSet(mods))
        for (String mod : mods)
        {
          String tag = mod.substring(0, mod.indexOf('(')).trim(), m = mod.substring(mod.indexOf('(')+1, mod.indexOf(')'));
          if      (Strs.equals(tag, "N-Term")) match.addModificationMatch(ModAttachment.N_TERM, new Modification(m, new NumericMass(0d)));
          else if (Strs.equals(tag, "C-Term")) match.addModificationMatch(ModAttachment.C_TERM, new Modification(m, new NumericMass(0d)));
          else
          {
            match.addModificationMatch(new Integer(tag.substring(1, tag.length()))-1, new Modification(m, new NumericMass(0d)));
          }
        }
      match.addProteinMatch(new PeptideProteinMatch(file.get("Leading proteins"),
          Optional.of("-"), Optional.of("-"), Optional.of("-"), PeptideProteinMatch.HitType.TARGET));

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      match.setMassDiff(file.getDouble("DeltaM [ppm]")*id.getPrecursorMz().get()*id.getAssumedCharge().get()*1E-6);
//      match.setNumMatchedIons(new Integer(matched[0]));
//      match.setTotalNumIons(new Integer(matched[1]));
      match.setNumMissedCleavages(file.getInt("# Missed Cleavages"));
      match.setRank(file.getInt("Rank"));
      PSMs.addScore(match, "XCorr", file.getDouble("XCorr"));
      PSMs.addScore(match, "Percolator q-Value", file.getDouble("Percolator q-Value"));
      PSMs.addScore(match, "Percolator PEP",     file.getDouble("Percolator PEP"));
      PSMs.addScore(match, "DeltaScore",         file.getDouble("DeltaScore"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }

    return id_match;
  }
*/
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMSGFplus(Collection<String> files) throws IOException
  {
    if (Tools.isSet(files))
    {
      // the totals
      Multimap<SpectrumIdentifier, PeptideMatch> matches = HashMultimap.create();
      for (String f : files)
      {
        matches.putAll(readMSGFplus(f));
      }
      return matches;
    }
    return null;
  }
//  Scan Number     Title   Sequence        Modifications   Protein Accessions      Amanda Score    Weighted Probability    Rank    m/z     Charge  RT      Filename
//  20130510_EXQ1_IgPa_QC_UPS1_01.34.34.4   mFVcSDTDYcRQQSEAKNQ     M1(Oxidation|15.994915|variable);C4(Carbamidomethyl|57.021464|fixed);C10(Carbamidomethyl|57.021464|fixed)       gi|253775272    3.42563717374274        0.454397865483082       1       596.5   4       11.2614798      20130510_EXQ1_IgPa_QC_UPS1_01.mgf
// TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readAmanda(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      // 20130510_EXQ1_IgPa_QC_UPS1_01.34.34.4
      String[] items = Strs.split(file.get("Title"), '.');
      String     run = Strs.stripLastOf(file.get("Filename"), '.'), scan = items[items.length-3];
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("m/z"));
        id.setPrecursorNeutralMass((id.getPrecursorMz().get() - 1.007825035d) * id.getAssumedCharge().get());
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = new PeptideMatch(file.get("Sequence").toUpperCase());
      // Sequence	Modifications
      // mFVcSDTDYcRQQSEAKNQ	M1(Oxidation|15.994915|variable);C4(Carbamidomethyl|57.021464|fixed);C10(Carbamidomethyl|57.021464|fixed)
      String[] mods = Strs.isSet(file.get("Modifications"))?Strs.split(file.get("Modifications"), ';'):null;
      if (Tools.isSet(mods))
        for (String mod : mods)
        {
          int left=mod.indexOf('('), right=mod.indexOf(')', left), pos = Stats.toInt(mod.substring(1, left));
          String r=mod.substring(0, 1);
          String[] def=Strs.split(mod.substring(left + 1, right), '|');
          // TODO not optimal for terminal cases
          match.addModificationMatch(pos-1, Stats.toDouble(def[1]));
        }

      // parse the protein names
      // gi|253775272
      String[] accs = Strs.split(file.get("Protein Accessions"), ';');
      if (Tools.isSet(accs))
        for (String acc : accs)
        {
          boolean decoy = (acc.indexOf("REV_")==0);
          String[]  acs = Strs.split(decoy?acc.substring(4):acc, '|');
          try
          {
            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
              Optional.of(acs[0]), Optional.of("-"), Optional.of("-"),
              decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
          }
          catch (ArrayIndexOutOfBoundsException e)
          {
            System.out.println(e.toString());
          }
        }

      match.setNeutralPeptideMass(match.toPeptide(PSMs.sNumModResolver).getMolecularMass());
      match.setMassDiff(id.getPrecursorNeutralMass().get()-match.getNeutralPeptideMass());
      match.setRank(Stats.toInt(file.get("Rank")));

      PSMs.addScore(match, "AmandaScore",         file.getDouble("Amanda Score"));
      PSMs.addScore(match, "WeightedProbability", file.getDouble("Weighted Probability"));

      // add as much information to the match as possible
      PSMs.addScore(match, "^Charge", file.getInt("Charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    //LCMSMS.rank(id_match, "MSGFScore");

    return id_match;
  }

  //  #SpecFile       SpecID  ScanNum FragMethod      Precursor       IsotopeError    PrecursorError(ppm)     Charge  Peptide Protein DeNovoScore     MSGFScore       SpecEValue      EValue  QValue  PepQValue
  //  20130510_EXQ1_IgPa_QC_UPS1_01.mzML      controllerType=0 controllerNumber=1 scan=61474  61474   HCD     1101.2928       0       10.197636       4       QVDPAALTVHYVTALGTDSFSQQMLDAWHGENVDTSLTQR        gi|253771643|ref|YP_003034474.1|(pre=R,post=M)  355     335     1.5431253692232283E-44  4.0382819349887276E-38  0.0     0.0
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMSGFplus(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("#SpecFile"), '.'), scan = file.get("ScanNum");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("Precursor"));
        id.setPrecursorNeutralMass((id.getPrecursorMz().get() - 1.007825035d) * id.getAssumedCharge().get());
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = PSMs.fromNumModSequence(file.get("Peptide"));
      // parse the protein names
      // XXX_gi|4826734|ref|NP_004951.1|(pre=K,post=W);XXX_gi|767988385|ref|XP_011544083.1|(pre=K,post=W);XXX_gi|283135173|ref|NP_001164408.1|(pre=K,post=W);XXX_gi|283135201|ref|NP_001164105.1|(pre=K,post=W);XXX_gi|530407875|ref|XP_005255290.1|(pre=K,post=W);XXX_gi|767988388|ref|XP_011544084.1|(pre=K,post=W)
      String[] accs = Strs.split(file.get("Protein"), ';');
      if (Tools.isSet(accs))
        for (String acc : accs)
        {
          boolean decoy = (acc.indexOf("XXX_")==0);
          String[]  acs = Strs.split(decoy?acc.substring(4):acc, '|');
          int       pre = acc.indexOf("pre=")+4, post = acc.indexOf("post=")+5;
          try
          {
            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
                Optional.of(acs[0]), Optional.of(acc.substring(pre,pre+1)), Optional.of(acc.substring(post,post+1)),
                decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
          }
          catch (ArrayIndexOutOfBoundsException e)
          {
            System.out.println(e.toString());
          }
        }

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      if (file.getDouble("PrecursorError(ppm)")!=null)
           match.setMassDiff(file.getDouble("PrecursorError(ppm)") * id.getPrecursorMz().get() * id.getAssumedCharge().get() * 1E-6);
      else match.setMassDiff(file.getDouble("PrecursorError(Da)"));

      PSMs.addScore(match, "DeNovoScore",  file.getDouble("DeNovoScore"));
      PSMs.addScore(match, "IsotopeError", file.getInt("IsotopeError"));
      PSMs.addScore(match, "MSGFScore",    file.getDouble("MSGFScore"));
      PSMs.addScore(match, "SpecEValue",   file.getDouble("SpecEValue"));
      PSMs.addScore(match, "EValue",       file.getDouble("EValue"));
      PSMs.addScore(match, "QValue", file.getDouble("QValue"));
      PSMs.addScore(match, "PepQValue", file.getDouble("PepQValue"));

      // add as much information to the match as possible
      match.setNeutralPeptideMass(id.getPrecursorNeutralMass().get() + match.getMassDiff());
      PSMs.addScore(match, "^Charge", file.getInt("Charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    LCMSMS.rank(id_match, "MSGFScore", true);

    return id_match;
  }
//  Filename        Spectrum Number Spectrum ID     Spectrum Title  Retention Time (minutes)        Precursor m/z   Precursor Intensity     Precursor Charge        Precursor Mass (Da)     Experimental Peaks      Total Intensity Peptide Sequence        Base Peptide Sequence   Protein Description     Start Residue Number    Stop Residue Number     Missed Cleavages        Theoretical Mass (Da)   Precursor Mass Error (Da)       Precursor Mass Error (ppm)      Matching Products       Total Products  Ratio of Matching Products      Matching Intensity      Fraction of Intensity Matching  Morpheus Score  Target? Decoy?  Cumulative Target       Cumulative Decoy        Q-Value (%)
//  X:\data\UPS_EColi_1\20130510_EXQ1_IgPa_QC_UPS1_01.mzML  67969   controllerType=0 controllerNumber=1 scan=67969          168.04589       1065.86999511719        7412378.5       3       3194.58815621156        316     17005826.3769531        R.TAGSSGANPFAC[carbamidomethylation of C]IAAGIASLWGPAHGGANEAALK.M       TAGSSGANPFACIAAGIASLWGPAHGGANEAALK      gi|253774310|ref|YP_003037141.1| citrate synthase I [Escherichia coli BL21(DE3)]        241     274     0       3194.5567307142 0.0314254973618517      9.83720121784345        45      66      0.681818181818182       4206046.93017578        0.247329758457134       45.2473297584571        True    False   1       0       0
// TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMorpheus(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("#SpecFile"), '.'), scan = file.get("ScanNum");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("Precursor"));
        id.setPrecursorNeutralMass((id.getPrecursorMz().get() - 1.007825035d) * id.getAssumedCharge().get());
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = PSMs.fromNumModSequence(file.get("Peptide"));
      // parse the protein names
      // XXX_gi|4826734|ref|NP_004951.1|(pre=K,post=W);XXX_gi|767988385|ref|XP_011544083.1|(pre=K,post=W);XXX_gi|283135173|ref|NP_001164408.1|(pre=K,post=W);XXX_gi|283135201|ref|NP_001164105.1|(pre=K,post=W);XXX_gi|530407875|ref|XP_005255290.1|(pre=K,post=W);XXX_gi|767988388|ref|XP_011544084.1|(pre=K,post=W)
      String[] accs = Strs.split(file.get("Protein"), ';');
      if (Tools.isSet(accs))
        for (String acc : accs)
        {
          boolean decoy = (acc.indexOf("XXX_")==0);
          String[]  acs = Strs.split(decoy?acc.substring(4):acc, '|');
          int       pre = acc.indexOf("pre=")+4, post = acc.indexOf("post=")+5;
          try
          {
            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
              Optional.of(acs[0]), Optional.of(acc.substring(pre,pre+1)), Optional.of(acc.substring(post,post+1)),
              decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
          }
          catch (ArrayIndexOutOfBoundsException e)
          {
            System.out.println(e.toString());
          }
        }

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      if (file.getDouble("PrecursorError(ppm)")!=null)
        match.setMassDiff(file.getDouble("PrecursorError(ppm)") * id.getPrecursorMz().get() * id.getAssumedCharge().get() * 1E-6);
      else match.setMassDiff(file.getDouble("PrecursorError(Da)"));

      PSMs.addScore(match, "DeNovoScore",  file.getDouble("DeNovoScore"));
      PSMs.addScore(match, "IsotopeError", file.getInt("IsotopeError"));
      PSMs.addScore(match, "MSGFScore",    file.getDouble("MSGFScore"));
      PSMs.addScore(match, "SpecEValue",   file.getDouble("SpecEValue"));
      PSMs.addScore(match, "EValue",       file.getDouble("EValue"));
      PSMs.addScore(match, "QValue", file.getDouble("QValue"));
      PSMs.addScore(match, "PepQValue", file.getDouble("PepQValue"));

      // add as much information to the match as possible
      match.setNeutralPeptideMass(id.getPrecursorNeutralMass().get() + match.getMassDiff());
      PSMs.addScore(match, "^Charge", file.getInt("Charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    LCMSMS.rank(id_match, "MSGFScore", true);

    return id_match;
  }
//  # id, scanNum, RT, mz(data), z, pepMass(denovo), err(data-denovo), ppm(1e6*err/(mz*z)), score, peptide, aaScore,
//  1, 0, 1.1, 419.3200, 2, 836.5848, 0.0407, 48.5, 0.1, PLLLLLR, 1-1-1-1-1-1-32
//  2, 0, 1.3, 596.7500, 4, 2383.0038, -0.0329, -13.8, 5.3, C(Cam)LC(Cam)MSSPDAWVSDRC(Cam)NRNR, 36-1-1-1-1-1-1-7-1-1-1-1-1-1-1-1-1-7-32
//  3, 0, 7.6, 1550.4600, 3, 4648.6858, -0.3276, -70.4, 0.0, HGC(Cam)C(Cam)C(Cam)C(Cam)DYKKLFGENRM(O)HC(Cam)C(Cam)C(Cam)NFGQC(Cam)C(Cam)C(Cam)C(Cam)GVTYM(O)K, 3-1-3-1-1-1-1-2-1-1-1-1-1-1-1-1-1-1-1-2-2-2-1-1-1-1-1-1-1-2-1-1-1-9-4
// TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readNovor(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String run = Strs.stripLastOf(file.get("#SpecFile"), '.'), scan = file.get("ScanNum");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorMz(file.getDouble("Precursor"));
        id.setPrecursorNeutralMass((id.getPrecursorMz().get() - 1.007825035d) * id.getAssumedCharge().get());
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = PSMs.fromNumModSequence(file.get("Peptide"));
      // parse the protein names
      // XXX_gi|4826734|ref|NP_004951.1|(pre=K,post=W);XXX_gi|767988385|ref|XP_011544083.1|(pre=K,post=W);XXX_gi|283135173|ref|NP_001164408.1|(pre=K,post=W);XXX_gi|283135201|ref|NP_001164105.1|(pre=K,post=W);XXX_gi|530407875|ref|XP_005255290.1|(pre=K,post=W);XXX_gi|767988388|ref|XP_011544084.1|(pre=K,post=W)
      String[] accs = Strs.split(file.get("Protein"), ';');
      if (Tools.isSet(accs))
        for (String acc : accs)
        {
          boolean decoy = (acc.indexOf("XXX_")==0);
          String[]  acs = Strs.split(decoy?acc.substring(4):acc, '|');
          int       pre = acc.indexOf("pre=")+4, post = acc.indexOf("post=")+5;
          try
          {
            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
              Optional.of(acs[0]), Optional.of(acc.substring(pre,pre+1)), Optional.of(acc.substring(post,post+1)),
              decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
          }
          catch (ArrayIndexOutOfBoundsException e)
          {
            System.out.println(e.toString());
          }
        }

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      if (file.getDouble("PrecursorError(ppm)")!=null)
        match.setMassDiff(file.getDouble("PrecursorError(ppm)") * id.getPrecursorMz().get() * id.getAssumedCharge().get() * 1E-6);
      else match.setMassDiff(file.getDouble("PrecursorError(Da)"));

      PSMs.addScore(match, "DeNovoScore",  file.getDouble("DeNovoScore"));
      PSMs.addScore(match, "IsotopeError", file.getInt("IsotopeError"));
      PSMs.addScore(match, "MSGFScore",    file.getDouble("MSGFScore"));
      PSMs.addScore(match, "SpecEValue",   file.getDouble("SpecEValue"));
      PSMs.addScore(match, "EValue",       file.getDouble("EValue"));
      PSMs.addScore(match, "QValue", file.getDouble("QValue"));
      PSMs.addScore(match, "PepQValue", file.getDouble("PepQValue"));

      // add as much information to the match as possible
      match.setNeutralPeptideMass(id.getPrecursorNeutralMass().get() + match.getMassDiff());
      PSMs.addScore(match, "^Charge", file.getInt("Charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    LCMSMS.rank(id_match, "MSGFScore", true);

    return id_match;
  }
//  Spectrum number, Filename/id, Peptide, E-value, Mass, gi, Accession, Start, Stop, Defline, Mods, Charge, Theo Mass, P-value, NIST score
//  171,20130510_EXQ1_IgPa_QC_UPS1_01.1158.1158.2,HFTAK,0.553698705712872,602.319,0,BL_ORD_ID:56,463,467,gi|253775657|ref|YP_003038488.1| tryptophanase [Escherichia coli BL21(DE3)],,2,602.318,0.00542841868345953,0
//  236,20130510_EXQ1_IgPa_QC_UPS1_01.1602.1602.2,SISASGHK,0.0530103870541474,785.405,0,BL_ORD_ID:2177,269,276,gi|253773536|ref|YP_003036367.1| glutamate decarboxylase [Escherichia coli BL21(DE3)],,2,785.403,0.00151458248726135,0
  public static Multimap<SpectrumIdentifier, PeptideMatch> readOMSSA(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    Map<String, Modification> modmap = new HashMap<>();
    modmap.put("oxidation of M",         UnimodManager.getModification("Hydroxylation").get());
    modmap.put("deamidation of N and Q", UnimodManager.getModification("Deamidation").get());
    modmap.put("pyro-glu from n-term Q", UnimodManager.getModification("Pyro-glu").get());

    TabFile file = new TabFile(filename, TabFile.comma);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String[] ids = Strs.split(file.get("Filename/id"), '.');
      String run = Strs.toString(Arrays.copyOfRange(ids, 0, ids.length-3), "."), scan = ids[ids.length-3];
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+ scan);
        id.setName(run + "#" + scan);
        id.setAssumedCharge(file.getInt("Charge"));
        id.setPrecursorNeutralMass(file.getDouble("Mass"));
        id.setPrecursorMz((id.getPrecursorNeutralMass().get() + 1.007825035d * id.getAssumedCharge().get()) /id.getAssumedCharge().get());
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      PeptideMatch match = new PeptideMatch(file.get("Peptide").toUpperCase());
      // add the modifications if necessary: YHSETEmmR; oxidation of M:7 ,oxidation of M:8
      if (Strs.isSet(file.get("Mods")))
      {
        String[] mods = Strs.split(file.get("Mods"), ',', true);
        for (String mod : mods)
        {
          String[] mm = Strs.split(mod, ':');
          int pos = Stats.toInt(mm[1]);
          if (modmap.containsKey(mm[0])) match.addModificationMatch(pos, modmap.get(mm[0]));
          else
          {
            System.out.println("Unknown mod: " + mod);
          }
        }
      }
      // gi|253775657|ref|YP_003038488.1| tryptophanase [Escherichia coli BL21(DE3)]
      boolean decoy = (file.get("Defline").indexOf("XXX_")==0);
      String accs = Strs.split(file.get("Defline"), ' ')[0];
      if (Strs.isSet(accs))
      {
        String[]  acs = Strs.split(decoy?accs.substring(4):accs, '|');
        try
        {
          match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
            Optional.of(acs[0]), Optional.of("-"), Optional.of("-"),
            decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
          System.out.println(e.toString());
        }
      }

      PSMs.addScore(match, "OMSSA:EVal", file.getDouble("E-value"));
      PSMs.addScore(match, "OMSSA:PVal", file.getDouble("P-value"));
      PSMs.addScore(match, "OMSSA:NIST", file.getDouble("NIST score"));

      // add as much information to the match as possible
      match.setNeutralPeptideMass(file.getDouble("Theo Mass"));
      match.setMassDiff(id.getPrecursorNeutralMass().get() - match.getNeutralPeptideMass());

      PSMs.addScore(match, "^Charge", file.getInt("Charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    LCMSMS.rank(id_match, "OMSSA:EVal", false);

    return id_match;
  }
//  N       Unused  Total   %Cov    %Cov(50)        %Cov(95)        Accessions      Names   Used    Annotation      Contrib Conf    Sequence        Modifications   ProteinModifications    Cleavages       dMass   Obs MW  Obs m/z Theor MW        Theor m/z       Theor z Sc      Spectrum        Acq Time        Intensity (Peptide)     PrecursorIntensityAcquisition   Apex Time (Peptide)     Elution Peak Width (Peptide)    MS2Counts
//  1       187.69  187.69  88.9800012111664        80.0999999046326        76.5500009059906        gi|253775383    DNA-directed RNA polymerase, beta' subunit [Escherichia coli BL21(DE3)]                 2       99.0000009536743        AAAESSIQVK                              -0.00113623996730894    1002.53344726563        502.274 1002.53454589844        502.274566650391        2       11      1.1.1.6275.1    29.61068        3.156882E+08
// TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readProteinPilot(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

//    UnimodModificationResolver modResolver = new UnimodModificationResolver();
//    modResolver.putTranslate("de", "Deamidated");
//    modResolver.putTranslate("ox", "Oxidation");
//    modResolver.putTranslate("ac", "Acetyl");
//    modResolver.putTranslate("gl", "Gln->pyro-Glu");
//
    ProteinPilotReader ppReader = new ProteinPilotReader();
    PsmReaders              psm = new PsmReaders();
    ppReader.parse(new File(filename), psm.insertIdMatch);

    System.out.println(psm.mIdMatch.size());

    return psm.mIdMatch;

//    TabFile file = new TabFile(filename, TabFile.tabb);
//    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
//    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
//    while (file.hasNext())
//    {
//      String run = Strs.stripLastOf(file.get("#SpecFile"), '.'), scan = file.get("ScanNum");
//      SpectrumIdentifier id = run_scan_id.get(run, scan);
//      if (id==null)
//      {
//        id = new SpectrumIdentifier(run+"#"+scan);
//        id.setName(run + "#" + scan);
//        id.setAssumedCharge(file.getInt("Charge"));
//        id.setPrecursorMz(file.getDouble("Precursor"));
//        id.setPrecursorNeutralMass((id.getPrecursorMz().get() - 1.007825035d) * id.getAssumedCharge().get());
//        id.setSpectrumFile(run);
//        id.addScanNumber(new Integer(scan));
//        run_scan_id.put(run, scan, id);
//      }
//      PeptideMatch match = PSMs.fromNumModSequence(file.get("Peptide"));
//      // parse the protein names
//      // XXX_gi|4826734|ref|NP_004951.1|(pre=K,post=W);XXX_gi|767988385|ref|XP_011544083.1|(pre=K,post=W);XXX_gi|283135173|ref|NP_001164408.1|(pre=K,post=W);XXX_gi|283135201|ref|NP_001164105.1|(pre=K,post=W);XXX_gi|530407875|ref|XP_005255290.1|(pre=K,post=W);XXX_gi|767988388|ref|XP_011544084.1|(pre=K,post=W)
//      String[] accs = Strs.split(file.get("Protein"), ';');
//      if (Tools.isSet(accs))
//        for (String acc : accs)
//        {
//          boolean decoy = (acc.indexOf("XXX_")==0);
//          String[]  acs = Strs.split(decoy?acc.substring(4):acc, '|');
//          int       pre = acc.indexOf("pre=")+4, post = acc.indexOf("post=")+5;
//          try
//          {
//            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
//              Optional.of(acs[0]), Optional.of(acc.substring(pre,pre+1)), Optional.of(acc.substring(post,post+1)),
//              decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
//          }
//          catch (ArrayIndexOutOfBoundsException e)
//          {
//            System.out.println(e.toString());
//          }
//        }
//
////      String[] matched = Strs.split(file.get("Ions Matched"), '/');
//      if (file.getDouble("PrecursorError(ppm)")!=null)
//        match.setMassDiff(file.getDouble("PrecursorError(ppm)") * id.getPrecursorMz().get() * id.getAssumedCharge().get() * 1E-6);
//      else match.setMassDiff(file.getDouble("PrecursorError(Da)"));
//
//      PSMs.addScore(match, "DeNovoScore",  file.getDouble("DeNovoScore"));
//      PSMs.addScore(match, "IsotopeError", file.getInt("IsotopeError"));
//      PSMs.addScore(match, "MSGFScore", file.getDouble("MSGFScore"));
//      PSMs.addScore(match, "SpecEValue",   file.getDouble("SpecEValue"));
//      PSMs.addScore(match, "EValue", file.getDouble("EValue"));
//      PSMs.addScore(match, "QValue", file.getDouble("QValue"));
//      PSMs.addScore(match, "PepQValue", file.getDouble("PepQValue"));
//
//      // add as much information to the match as possible
//      match.setNeutralPeptideMass(id.getPrecursorNeutralMass().get() + match.getMassDiff());
//      PSMs.addScore(match, "^Charge", file.getInt("Charge"));
//
//      if (!id_match.put(id, match))
//      {
////        System.out.println("Duplicated?");
//      }
//    }
//    LCMSMS.rank(id_match, "MSGFScore");

//    return id_match;
  }
//  scan    charge  spectrum precursor m/z  spectrum neutral mass   peptide mass    delta_cn        sp score        sp rank xcorr score     xcorr rank      b/y ions matched        b/y ions total  distinct matches/spectrum       sequence        cleavage type   protein id      flanking aa     original target sequence
//  10254   2       300.175 598.335 598.344 0.0402319       15.4584 1       0.188466        1       2       10      1       PNAVAK  trypsin/p-full-digest   gi|253772588|ref|YP_003035419.1|(12)    KN      PNAVAK
//  10254   2       300.175 598.335 598.344 0       15.4584 2       0.148234        2       2       10      1       PANAVK  trypsin/p-full-digest   decoy_gi|253772588|ref|YP_003035419.1|(12)      KN      PNAVAK
// TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readCrux(String filename) throws IOException
  {
    System.out.println("fetching the PSM from " + filename);

    String run = FilenameUtils.getBaseName(filename);
    if (run.indexOf("Crux_")==0) run = run.substring(5);
    // Strs.stripLastOf(filename, '.');

    TabFile file = new TabFile(filename, TabFile.tabb);
    Table<String, String, SpectrumIdentifier> run_scan_id = HashBasedTable.create();
    Multimap<SpectrumIdentifier, PeptideMatch> id_match   = HashMultimap.create();
    while (file.hasNext())
    {
      String scan = file.get("scan");
      SpectrumIdentifier id = run_scan_id.get(run, scan);
      if (id==null)
      {
        id = new SpectrumIdentifier(run+"#"+ scan);
        id.setName(run+"#"+scan);
        id.setAssumedCharge(file.getInt("charge"));
        id.setPrecursorMz(file.getDouble("spectrum precursor m/z"));
        id.setPrecursorNeutralMass(file.getDouble("spectrum neutral mass"));
        id.setSpectrumFile(run);
        id.addScanNumber(new Integer(scan));
        run_scan_id.put(run, scan, id);
      }
      // IETLMRNLM[15.9949]PWRK
      PeptideMatch match = PSMs.fromNumModSequence(file.get("sequence"));
      // parse the protein names
      // XXX_gi|4826734|ref|NP_004951.1|(pre=K,post=W);XXX_gi|767988385|ref|XP_011544083.1|(pre=K,post=W);XXX_gi|283135173|ref|NP_001164408.1|(pre=K,post=W);XXX_gi|283135201|ref|NP_001164105.1|(pre=K,post=W);XXX_gi|530407875|ref|XP_005255290.1|(pre=K,post=W);XXX_gi|767988388|ref|XP_011544084.1|(pre=K,post=W)
      String flanking = file.get("flanking aa");
      String[]   accs = Strs.split(file.get("protein id"), ';');
      if (Tools.isSet(accs))
        for (String acc : accs)
        {
          boolean decoy = (acc.indexOf("decoy_")==0);
          String[]  acs = Strs.split(decoy?acc.substring(6):acc, '|');
          try
          {
            match.addProteinMatch(new PeptideProteinMatch(acs.length>1?acs[1]:"unknown",
              Optional.of(acs[0]), Optional.of(Strs.isSet(flanking)?flanking.substring(0,1):"-"),
              Optional.of((flanking!=null&&flanking.length()>1)?flanking.substring(1,2):"-"),
              decoy? PeptideProteinMatch.HitType.DECOY:PeptideProteinMatch.HitType.TARGET));
          }
          catch (ArrayIndexOutOfBoundsException e)
          {
            System.out.println(e.toString());
          }
        }

//      String[] matched = Strs.split(file.get("Ions Matched"), '/');
      Peptide p = match.toPeptide(new NumModMatchResolver());

      PSMs.addScore(match, "delta_cn", file.getDouble("delta_cn"));
      PSMs.addScore(match, "sp score", file.getDouble("sp score"));
      PSMs.addScore(match, "xcorr score",    file.getDouble("xcorr score"));
      PSMs.addScore(match, "b/y ions matched", file.getDouble("b/y ions matched"));
      PSMs.addScore(match, "b/y ions total", file.getDouble("b/y ions total"));
      PSMs.addScore(match, "sp rank", file.getInt("sp rank"));

      // add as much information to the match as possible
      match.setNeutralPeptideMass(p.getMolecularMass());
      match.setMassDiff(match.getNeutralPeptideMass() - p.getMolecularMass());

      PSMs.addScore(match, "^Charge", file.getInt("charge"));

      if (!id_match.put(id, match))
      {
//        System.out.println("Duplicated?");
      }
    }
    LCMSMS.rank(id_match, "xcorr score", true);

    return id_match;
  }
  public static Multimap<SpectrumIdentifier, PeptideMatch> readMzID(String filename)
  {
    Multimap<SpectrumIdentifier, PeptideMatch> id_match = HashMultimap.create();

    System.out.println("Reading " + filename);

    PsmReaders readers = new PsmReaders();
    readers.parseMSGFplusMzID(filename);

    Iterator<SpectrumIdentifier> idr = readers.getResultMap().keySet().iterator();
    while (idr.hasNext())
    {
      SpectrumIdentifier id = idr.next();
      id_match.putAll(id, readers.getResultMap().get(id));
      if (id_match.keySet().size()%1000==0) System.out.print(".");
    }
    System.out.print("\n --> " + readers.getResultMap().keySet().size() + "\n");
    return id_match;
  }
  // TODO to be completed
  public static Multimap<SpectrumIdentifier, PeptideMatch> readXTandem(String filename)
  {
    Multimap<SpectrumIdentifier, PeptideMatch> id_match = HashMultimap.create();

    System.out.println("Reading " + filename);

    PsmReaders readers = new PsmReaders();
    readers.parseMSGFplusMzID(filename);

    Iterator<SpectrumIdentifier> idr = readers.getResultMap().keySet().iterator();
    while (idr.hasNext())
    {
      SpectrumIdentifier id = idr.next();
      id_match.putAll(id, readers.getResultMap().get(id));
      if (id_match.keySet().size()%1000==0) System.out.print(".");
    }
    System.out.print("\n --> " + readers.getResultMap().keySet().size() + "\n");
    return id_match;
  }

  // TODO. Need more work in the future
  public static Dataframe fetchMzID(String root, double q, boolean save_decoy)
  {
    PsmReaders readers = new PsmReaders();

    List<String> files = IOs.listFiles(root, new FileFilterExtensions(new String[]{"mzid"}));
    if (Tools.isSet(files))
      for (String f : files)
      {
        System.out.println("Reading " + f);
        readers.parseMSGFplusMzID(f);
        // purge the matches
        if (!save_decoy || q<1)
        {
          System.out.print("Purging the low quality matches: " + readers.getResultMap().keySet().size());
          Iterator<SpectrumIdentifier> idr = readers.getResultMap().keySet().iterator();
          while (idr.hasNext())
          {
            List<PeptideMatch> matches = readers.getResultMap().get(idr.next());
            for (PeptideMatch match : matches)
            {
              if (match.getScore("MS-GF:QValue")>q ||
                  match.getProteinMatches().get(0).getHitType().equals(PeptideProteinMatch.HitType.DECOY)==save_decoy)
                idr.remove();

              break;
            }
          }
          System.out.print(" --> " + readers.getResultMap().keySet().size() + "\n");
        }
        break;
//        System.gc();
      }

    System.out.println("Preparing the dataframe");

    // output the PSMs in PD format
    Dataframe            df = new Dataframe("PSMs from " + root);
    Map<String, Object> row = new HashMap<>();
    long nrow = 0;
    for (SpectrumIdentifier id : readers.getResultMap().keySet())
    {
      // "Peptide","Mods","Accession","Charge","Rank","mz","ppm","Interference","InjectTime","RT","Scan1","Run","Score","q.val","Quan.Usage", channels
      List<PeptideMatch> matches = readers.getResultMap().get(id);
      for (PeptideMatch match : matches)
      {
        row.clear();
        row.put("Peptide", PSMs.toNumModSequence(match));
        Map<String, String> accs = Strs.toStrMap(Strs.split(match.getProteinMatches().get(0).getAccession(), '|'));
        for (String acc : accs.keySet())
          if (acc.indexOf("gi")>=0) row.put("Accession", accs.get(acc));
        if (row.get("Accession")==null) row.put("Accession",match.getProteinMatches().get(0).getAccession());
        row.put("Charge", id.getAssumedCharge().get());
        row.put("Rank", match.getRank());
        if (id.getPrecursorMz().isPresent()) row.put("mz", id.getPrecursorMz());
        row.put("ppm", 1E6 * match.getMassDiff() / id.getPrecursorNeutralMass().get());
        if (Tools.isSet(id.getRetentionTimes())) row.put("RT",    id.getRetentionTimes().getFirst().getTime());
        if (Tools.isSet(id.getScanNumbers()))    row.put("Scan1", id.getScanNumbers().getFirst().getValue());
        row.put("Run", Strs.stripLastOf(id.getSpectrumFile().get(), '.'));
        row.put("Score", match.getScore("MS-GF:RawScore"));
        row.put("q.val", match.getScore("MS-GF:QValue"));
        row.put("Decoy", match.getProteinMatches().get(0).getHitType().equals(PeptideProteinMatch.HitType.DECOY) ? 1:0);
        row.put("EValue", match.getScore("MS-GF:SpecEValue"));

        df.addRow((++nrow) + "", row);
        break; // TODO, keep the top one for now. Will test for homology next
      }
      if (nrow %5000==0) System.out.print(".");
    }
    df.init();

    return df;
  }
}
