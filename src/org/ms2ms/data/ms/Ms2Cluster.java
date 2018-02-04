package org.ms2ms.data.ms;

import org.expasy.mzjava.core.ms.Tolerance;
import org.expasy.mzjava.core.ms.peaklist.Peak;
import org.expasy.mzjava.core.ms.spectrum.MsnSpectrum;
import org.ms2ms.algo.Peaks;
import org.ms2ms.algo.Similarity;
import org.ms2ms.algo.Spectra;
import org.ms2ms.math.Stats;
import org.ms2ms.utils.Tools;

import java.util.*;

public class Ms2Cluster
{
  private MsnSpectrum mMaster; // a composite spectrum to represent the cluster

  private Ms2Pointer mHead;
  private Collection<Ms2Pointer> mMembers, mCandidates = new TreeSet<>(); // actual or possible members of the cluster

  public Ms2Cluster() { super(); }
  public Ms2Cluster(Ms2Pointer s) { super(); mHead=s; }

  public Ms2Pointer getHead() { return mHead; }
  public Ms2Cluster setHead(Ms2Pointer s) { mHead=s; return this; }
  public Collection<Ms2Pointer> getCandidates() { return mCandidates; }
  public Ms2Cluster addMember(Ms2Pointer s) { if (s!=null) mCandidates.add(s); return this; }

  public boolean contains(Ms2Pointer p)
  {
    if (Tools.equals(p, mHead) || Tools.contains(mCandidates, p)) return true;
    return false;
  }

  /// start the method section  ///

  // from the HEAD to the candidates at high cutoff
  public Ms2Cluster cluster(Map<Ms2Pointer, MsnSpectrum> spectra, OffsetPpmTolerance tol, double min_dp)
  {
    // quit if the master or input are not present!
    if (mHead==null || !Tools.isSet(spectra)) return null;

    // setting the seed spectrum
    MsnSpectrum HEAD = (mMaster!=null?mMaster:spectra.get(mHead));

    // the collections
    mMembers = new ArrayList<>();
    Collection<MsnSpectrum> members = new ArrayList<>();
    // setup the master first
    List<Peak> head = Spectra.toListOfPeaks(HEAD);
    for (Ms2Pointer member : mCandidates)
    {
      MsnSpectrum scan = spectra.get(member);
      if (scan!=null && Similarity.dp(head, Spectra.toListOfPeaks(scan), tol, true, true)>=min_dp)
      {
        mMembers.add(member); members.add(spectra.get(member));
      }
    }
    mMaster = Spectra.accumulate(HEAD, tol, 0.5f, members);

    // remove the local objects
    head = (List )Tools.dispose(head);
    members = Tools.dispose(members);

    return this;
  }
  // trim away the members in the cluster by the matching probability to the 'center'.
  public Ms2Cluster trimByMatchProb(Map<Ms2Pointer, MsnSpectrum> spectra, int regions, double cut)
  {
    // quit if the master or input are not present!
    if (mMaster==null || !Tools.isSet(spectra)) return null;

    // grab the index from our master first
    List<Peak> master = Similarity.index(Spectra.toListOfPeaks(mMaster), regions, 1, 5, 0);
    long bins = (long )(Math.log(2)/Math.log(1d+5E-6)), npks = master.size();

    Iterator<Ms2Pointer> itr = mCandidates.iterator();
    while (itr.hasNext())
    {
      MsnSpectrum scan = spectra.get(itr.next());
      if (scan!=null)
      {
        List<Peak> member = Similarity.index(Spectra.toListOfPeaks(scan), regions, 1, 5, 0);
        int overlap = Peaks.overlap_counts(master, member, 0.01, true);

        double prob = Stats.hypergeom(overlap, npks, member.size(), bins);

        // check the exit condition
        if (-1d*prob<cut) itr.remove();
        // clean up the objects
        member = (List )Tools.dispose(member);
      }
    }

    return this;
  }
  // trim the matching candidates by matching probability
  public Ms2Cluster trimByDotP(Map<Ms2Pointer, MsnSpectrum> spectra, Tolerance tol, double min_dp)
  {
    // quit if the master or input are not present!
    if (mMaster==null || !Tools.isSet(spectra)) return null;

    // setup the master first
    List<Peak>        master = Spectra.toListOfPeaks(mMaster);
    Iterator<Ms2Pointer> itr = mCandidates.iterator();
    while (itr.hasNext())
    {
      MsnSpectrum scan = spectra.get(itr.next());
      if (scan!=null && Similarity.dp(master, Spectra.toListOfPeaks(scan), tol, true, true)<min_dp) itr.remove();
    }

    return this;
  }

}