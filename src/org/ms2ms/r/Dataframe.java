package org.ms2ms.r;

import com.google.common.collect.*;
import org.ms2ms.utils.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: wyu
 * Date: 7/13/14
 * Time: 2:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class Dataframe
{
  private String                     mTitle;
  private boolean                    mKeepData = true;
  private List<String>               mRowIDs;
  private List<Var>                  mColVars;
  private Map<String, Var>           mNameVar;
  private Table<String, Var, Object> mData;

  public Dataframe()                                         { super(); }
  public Dataframe(String cvs, char delim, String... idcols) { super(); readTable(cvs, delim, idcols); setTitle(cvs); }

  //** factory method **//
  public static Dataframe csv(String csv, char delim, String... idcols)
  {
    Dataframe data = new Dataframe();
    data.readTable(csv, delim, idcols); data.setTitle(csv);
    return data;
  }

  //** Getters the Setters **//
  public int size() { return mData!=null?mData.rowKeySet().size():0; }
  public String       getTitle()      { return mTitle; }
  public List<Var>    getVars()       { return mColVars; }
  public List<String> getRowIds()     { return mRowIDs; }
  public String       getRowId(int i) { return mRowIDs!=null?mRowIDs.get(i):null; }
  public Var[]        toVars(String... s)
  {
    if (!hasVars(s)) return null;
    Var[] out = new Var[s.length];
    for (int i=0; i<s.length; i++) out[i] = getVar(s[i]);

    return out;
  }
  public Dataframe setTitle(String s) { mTitle=s; return this; }

  public Map<Var,    Object> row(int    i) { return mData!=null?mData.row(getRowIds().get(i)):null; }
  public Map<Var,    Object> row(String s) { return mData!=null?mData.row(s):null; }
  public Map<String, Object> col(Var    s) { return mData!=null?mData.column(s):null; }
  public Var getVar(String s)
  {
    if (mNameVar==null)
    {
      mNameVar= new HashMap<String, Var>();
      for (Var v : mColVars) mNameVar.put(v.toString(), v);
    }
    return mNameVar.get(s);
  }
  public Dataframe put(String row, Var col, Object val)
  {
    if (mData==null) mData = HashBasedTable.create();
    mData.put(row, col, val);
    // update the variable cache
    addVar(col);

    return this;
  }
  public Dataframe put(Var col, Object val)
  {
    if (mData  ==null) mData = HashBasedTable.create();
    return put(mData.rowKeySet().size() + "", col, val);
  }
  public Dataframe put(String row, String col, Object val)
  {
    return put(row, hasVars(col)?getVar(col):new Variable(col), val);
  }
  public Dataframe put(String col, Object val)
  {
    if (mData  ==null) mData = HashBasedTable.create();
    return put(mData.rowKeySet().size()+"", hasVars(col)?getVar(col):new Variable(col), val);
  }
  public Dataframe addRowId(String row)
  {
    if (mRowIDs==null) mRowIDs = new ArrayList<String>();
    mRowIDs.add(row); return this;
  }
  public Dataframe addRow(String id, Map<Var, Object> row)
  {
    if (mData==null) mData = HashBasedTable.create();
    for (Var v : row.keySet())
      mData.put(id, v, row.get(v));

    return this;
  }
  public Var addVar(Var v)
  {
    if (mNameVar==null) mNameVar = new HashMap<String, Var>();
    if (mNameVar.put(v.toString(), v)==null)
    {
      // add to the var list if this is a new one
      if (mColVars==null) mColVars = new ArrayList<Var>();
      mColVars.add(v);
    }
    return v;
  }
  public boolean hasVars(String... vs)
  {
    if (!Tools.isSet(vs)) return false;
    for (String v : vs) if (getVar(v)==null) return false;

    return true;
  }
  public boolean hasVar(String s, boolean isCategorical)
  {
    if (Tools.isSet(s) && getVar(s)!=null && getVar(s).isCategorical()==isCategorical) return true;
    return false;
  }
  public boolean hasVar(Var s)
  {
    return (s!=null && mColVars!=null && mColVars.contains(s));
  }
  public Object[] get(String rowid, Var... vs)
  {
    Object[] lead = new Object[vs.length];
    for (int i=0; i<vs.length; i++) lead[i]=row(rowid).get(vs[i]);

    return lead;
  }
  public Object[] get(String rowid, String... vs)
  {
    Object[] lead = new Object[vs.length];
    for (int i=0; i<vs.length; i++) lead[i]=row(rowid).get(getVar(vs[i]));

    return lead;
  }
  public Object cell(String rowid, Var v) { return mData!=null?mData.get(rowid,v):null; }
  public Object cell(String rowid, String s) { return mData!=null?mData.get(rowid,getVar(s)):null; }
  public List<Var> cols() { return mColVars; }
  public List<String> rows() { return mRowIDs; }

  public Dataframe reorder(String... s)
  {
    if (!Tools.isSet(s) || mData==null || !Tools.isSet(mColVars) || !Tools.isSet(mNameVar)) return this;

    mColVars = new ArrayList<Var>(s.length);
    for (String v : s) if (mNameVar.containsKey(v)) mColVars.add(mNameVar.get(v));
    return this;
  }
  // simulate optional var so it's OK to call display(). Only the first element of the array is used
  public StringBuffer display(StringBuffer... bufs)
  {
    StringBuffer buf = (Tools.isSet(bufs) && bufs[0]!=null) ? bufs[0] : new StringBuffer();
    buf.append("rowid\t" + Strs.toString(getVars(), "\t") + "\n");
    for (String id : getRowIds())
    {
      buf.append(id);
      for (Var v : getVars())
        buf.append("\t" + (get(id, v)!=null&&get(id, v)[0]!=null?get(id, v)[0]:"--"));

      buf.append("\n");
    }
    return buf;
  }
  public void write(Writer writer, String delim)
  {
    try
    {
      writer.write("rowid" + delim + Strs.toString(getVars(), delim) + "\n");
      for (String id : getRowIds())
      {
        writer.write(id);
        for (Var v : getVars())
          writer.write(delim + (get(id, v)!=null&&get(id, v)[0]!=null?get(id, v)[0]:""));

        writer.write("\n");
      }
    }
    catch (IOException io)
    {
      throw new RuntimeException("Failed to write the data frame to the output.", io);
    }
  }
  public SortedMap<Double, Double> getXY(String x, String y)
  {
    if (!hasVar(x,false) || hasVar(y,false)) return null;

    SortedMap<Double, Double> line = new TreeMap<Double, Double>();
    Var vx=getVar(x), vy=getVar(y);
    for (String id : getRowIds())
    {
      Tools.putNotNull(line, cell(id, vx), cell(id, vy));
    }
    return line;
  }
  public double[] getDoubleCol(String y)
  {
    if (!Tools.isSet(mData) || !hasVars(y)) return null;

    double[] ys = new double[getRowIds().size()];
    Var      vy = getVar(y);
    for (int i=0; i<getRowIds().size(); i++)
    {
      ys[i] = Stats.toDouble(cell(getRowIds().get(i), vy));
    }
    return ys;
  }
  public Dataframe addVar(String v, double[] ys)
  {
    if (hasVars(v)) throw new RuntimeException("Variable " + v + " already exist!");

    if (ys!=null && ys.length==getRowIds().size())
    {
      Var vv = addVar(new Variable(v));
      for (int i=0; i<ys.length; i++)
      {
        put(getRowId(i), vv, ys[i]);
      }
    }
    return this;
  }
  //** builders **//
  public void readTable(String src, char delimiter, String... idcols)
  {
    if (!IOs.exists(src)) return;

    System.out.println("Reading the data table from " + src);
    TabFile csv=null;
    try
    {
      csv = new TabFile(src, delimiter);
      // convert the header to variables
      mColVars = new ArrayList<Var>();
      mData    = HashBasedTable.create();
      for (String col : csv.getHeaders()) mColVars.add(new Variable(col));
      // going thro the rows
      long row_counts = 0;
      while (csv.hasNext())
      {
        if (++row_counts % 10000  ==0) System.out.print(".");
        if (  row_counts % 1000000==0) System.out.println();
        String id=null;
        if (Tools.isSet(idcols))
        {
          for (String col : idcols)
            id= Strs.extend(id, csv.get(col), "_");
        }
        else id = row_counts+"";

        addRowId(id);
        // deposite the cells
        for (Var v : mColVars)
          mData.put(id, v, csv.get(v.toString()));
      }
      csv.close();
      System.out.println();
      // setup the types
      init();
    }
    catch (IOException ioe)
    {
      throw new RuntimeException("Unable to access file: " + src, ioe);
    }
  }
  // go thro the table to determine the type of the variables. Convert them to number if necessary
  protected void init()
  {
    if (!Tools.isSet(mData)) return;
    if (mRowIDs ==null) { mRowIDs  = new ArrayList<String>(mData.rowKeySet()); Collections.sort(mRowIDs); }
    if (mColVars==null)
    {
      mColVars = new ArrayList<Var>(mData.columnKeySet());
      mNameVar = new HashMap<String, Var>(mColVars.size());
      for (Var v : mColVars) mNameVar.put(v.toString(), v);
    }

    //int counts=0;
    for (Var v : mColVars)
    {
      v.setFactors(null); init(v);
    }
    System.out.println();
  }
  protected void init(Var v)
  {
    if (!Tools.isSet(mData)) return;

    boolean       isNum=true;
    Set<Object> factors=new HashSet<Object>();
    for (String row : mRowIDs)
    {
      Object val = Stats.toNumber(mData.get(row, v));

      if (val instanceof String) isNum=false;
      if (val!=null && (!(val instanceof String) || ((String )val).length()>0)) factors.add(val);
      // put the cell back
      if (row!=null && v!=null && val!=null) mData.put(row, v, val);
    }
    if (v.isType(Var.VarType.UNKNOWN))
    {
      if (factors.size()>0 && (!isNum || factors.size()<Math.min(250, mRowIDs.size()*0.25)))
        v.setType(Var.VarType.CATEGORICAL);
      else v.setType(Var.VarType.CONTINOUOUS);
    }
    if ( v.isCategorical())
    {
      v.setFactors(factors);
      //Collections.sort(v.getFactors());
    }

    Tools.dispose(factors);
  }

  //********** R or Matlab style algorithms ***************//

  /** Split the data frame by the factors in variable 'v'
   *
   * @param v
   * @return
   */
  public Map<Object, Dataframe> split(String v)
  {
    if (v==null || !hasVar(v,true)) return null;

    Map<Object, Dataframe> outs = new HashMap<Object, Dataframe>();
    Var vv = getVar(v);
    for (String r : getRowIds())
    {
      Object key = row(r).get(vv);
      Dataframe F = outs.get(key);
      if (F==null) F = new Dataframe();
      F.addRow(r, mData.row(r));
      outs.put(key, F.setTitle(key.toString()));
    }
    for (Dataframe d : outs.values()) d.init();

    return outs;
  }
  /** Partial implementation of R-aggregate
   *
   * @param by is a list of grouping categorical variables,
   * @return
   */
  public Dataframe aggregate(String... by)
  {
    Dataframe stats = new Dataframe();

    return stats;
  }
  public Dataframe melt(String... idvars)
  {
    return null;
  }

  /** generic transformation of a data frame in the style of Matlab
   *
   * pivot( [dose sbj], visit_name ) produces the following table

   []               []    'visit_name'    'visit_name'
   'dose'      'sbj'            'D0'            'D22'
   'dosed'    '1003'    [         1]    [         1]
   'dosed'    '1015'    [         1]    [         1]
   'dosed'    '1025'    [         1]    [         1]
   *
   * @param col is a categorical column whose factors will be used as the column header in the outgoing data frame
   * @param val is a numberic column whose values will be the cell in the outgoing data frame
   * @param func is the aggregate function if multiple values are found in a cell
   * @param rows are the columns that will transferred to the outgoing data frame
   * @return the outgoing data frame
   */
  public Dataframe pivot(String col, String val, Stats.Aggregator func, String... rows)
  {
    // make sure the column types are OK
    if (!hasVars(rows) || !hasVar(col, true)) return null;
    // looping thro the rows
    Var vcol=getVar(col), vval=getVar(val); Var[] vrows=Variable.toVars(this,rows);
    // build the inventory
    ListMultimap<ArrayKey, Object> body = ArrayListMultimap.create();
    for (String rowid : getRowIds())
    {
      body.put(new ArrayKey(ObjectArrays.concat(get(rowid, vcol)[0], get(rowid, vrows))), get(rowid, vval)[0]);
    }
    // construct the outgoing data frame
    Dataframe out = new Dataframe();
    for (ArrayKey keys : body.keySet())
    {
      String id = Strs.toString(Arrays.copyOfRange(keys.key, 1, keys.key.length), "");
      for (int i=1; i< keys.key.length; i++)
      {
        out.put(id, vrows[i-1], keys.key[i]);
      }
      out.put(id, keys.key[0].toString(), Stats.aggregate(body.get(keys), func));
    }
    out.init(); Tools.dispose(body);
    out.reorder(ObjectArrays.concat(rows, Strs.toStringArray(vcol.getFactors()), String.class));

    return out;
  }
  public TreeBasedTable<Double, Double, String> index(String row, String col)
  {
    if (!hasVar(row,false) || !hasVar(col,false)) return null;

    TreeBasedTable<Double, Double, String> indice = TreeBasedTable.create();
    Var vrow=getVar(row), vcol=getVar(col);
    for (String rowid : getRowIds())
      indice.put(Stats.toDouble(cell(rowid, vrow)), Stats.toDouble(cell(rowid, vcol)), rowid);

    return indice;
  }

  public static TreeBasedTable<Double, Double, String>[] indice(String row, String col, Dataframe... frames)
  {
    if (!Tools.isSet(frames) || !Tools.isSet(row) || !Tools.isSet(col)) return null;

    TreeBasedTable<Double, Double, String>[] indices = new TreeBasedTable[frames.length];
    for (int i=0; i<frames.length; i++)
    {
      indices[i] = frames[i].index(row, col);
    }
    return indices;
  }

  //** algorithms **//

  /**The returned data frame will contain:

   columns: all columns present in any provided data frame
   rows:    a set of rows from each provided data frame, with values in columns not present in the given data frame
            filled with missing (NA) values.

   The data type of columns will be preserved, as long as all data frames with a given column name agree on the
   data type of that column. If the data frames disagree, the column will be converted into a character strings.
   The user will need to coerce such character columns into an appropriate type.
   *
   * @param frames
   * @return
   */
  public static Dataframe smartbind(Dataframe... frames)
  {
    // prepare the merged columns
    Set<Var> cols = new TreeSet<Var>(); int order=0;
    for (Dataframe F : frames)
    {
      if (!Tools.isSet(F.getTitle())) F.setTitle(""+order++);
      cols.addAll(F.cols());
    }
    // the resulting dataframe
    Dataframe output = new Dataframe();
    for (Dataframe frame : frames)
    {
      for (Var v : cols)
        for (String r : frame.rows())
          if (frame.hasVar(v)) output.put(frame.getTitle()+"::"+r, v, frame.cell(r,v));
          // no value set if col didn;t exist for this dataframe. In R-routine, NA would the be the default
    }
    return output;
  }

  /** Merge two data frames by common columns or row names, or do other versions of database join operations.
   * animals *
   size   type   name
   small  cat    lynx
   big    cat    tiger
   small  dog    chihuahua
   big    dog   "great dane"

   * observations *
   number size  type
   1      big   cat
   2      small dog
   3      small dog
   4      big   dog

   * obs2 *
          number  size    type
   1      1       big     cat
   2      2       small   dog
   3      3       small   dog
   4      4       big     dog
   5      5       big     dog
   6      6       big     dog

   merge(observations, animals, c("size","type"))
   size   type  number    name
   big    cat   1         tiger
   big    dog   4         great dane
   small  dog   2         chihuahua
   small  dog   3         chihuahua

   > merge(obs2, animals, "size")
          size    number  type.x  type.y    name
   1      big     1       cat     cat       tiger
   2      big     1       cat     dog       great dane
   3      big     4       dog     cat       tiger
   4      big     4       dog     dog       great dane
   5      big     5       dog     cat       tiger
   6      big     5       dog     dog       great dane
   7      big     6       dog     cat       tiger
   8      big     6       dog     dog       great dane
   9      small   2       dog     cat       lynx
   10     small   2       dog     dog       chihuahua
   11     small   3       dog     cat       lynx
   12     small   3       dog     dog       chihuahua

   > merge(animals, obs2, "size")
          size   type.x  name         number  type.y
   1      big     cat   tiger         1       cat
   2      big     cat   tiger         4       dog
   3      big     cat   tiger         5       dog
   4      big     cat   tiger         6       dog
   5      big     dog   great dane    1       cat
   6      big     dog   great dane    4       dog
   7      big     dog   great dane    5       dog
   8      big     dog   great dane    6       dog
   9      small   cat   lynx          2       dog
   10     small   cat   lynx          3       dog
   11     small   dog   chihuahua     2       dog
   12     small   dog   chihuahua     3       dog

   * @param x and y : the data frames to be merged
   * @param allx and ally : TRUE if the rows from x/y will be added to the output that has no matching in the other.
   * @return the dataframe with the merge data
   */
  public static Dataframe merge(Dataframe x, Dataframe y, boolean allx, boolean ally, String... by)
  {
    if (x==null || y==null) return null;
    // get the shared cols
    String[] shared=Strs.toStringArray(Sets.intersection(Sets.newHashSet(x.cols()), Sets.newHashSet(y.cols())));
    // set the by cols to the common if not specified
    if (!Tools.isSet(by)) by=shared;
    if (!Tools.isSet(by)) return null;
    // pool the matching rows
    Table<String, String, String> id_x_y = HashBasedTable.create();
    for (String r : x.rows())
    {
      id_x_y.put(Strs.toString(x.get(r, by),"^"), ;

    }

    // create the merged cols
    Collection<String> col_merged = new HashSet<String>();
    for (Var v : x.cols())
      col_merged.add(!Tools.contains(shared, v.toString()) || Tools.contains(by, v.toString()) ? v.toString() : v.toString()+".x");
    for (Var v : y.cols())
      col_merged.add(!Tools.contains(shared, v.toString()) || Tools.contains(by, v.toString()) ? v.toString() : v.toString()+".y");

    // create the merged results
    Dataframe out = new Dataframe();
    for (String r : x.rows())
    {
      // check if the row meets the id col requirement
    }
    for (Var v : x.cols())
    {
        for (String c : col_merged)
        {

        }
      }
    }

  }
}
