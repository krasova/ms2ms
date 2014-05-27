package org.ms2ms.test;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by wyu on 4/13/14.
 */
public class HbaseExample01 extends TestAbstract
{
  Configuration config;
  // This instantiates an HTable object that connects you to the "myLittleHBaseTable" table.
  HTable        table;

  @Before
  public void setUp() throws IOException
  {
    config = HBaseConfiguration.create();
    // This instantiates an HTable object that connects you to the "myLittleHBaseTable" table.
    table  = new HTable(config, "myLittleHBaseTable");

    HConnection conn = HConnectionManager.createConnection(config);

    // When the cluster connection is established get an HTableInterface for each operation or thread.
    // HConnection.getTable(...) is lightweight. The table is really just a convenient place to call
    // table method and for a temporary batch cache.
    // It is in fact less overhead than HTablePool had when retrieving a cached HTable.
    // The HTableInterface returned is not thread safe as before.
    // It's fine to get 1000's of these.
    // Don't cache the longer than the lifetime of the HConnection
    HTableInterface table = conn.getTable("myLittleHBaseTable");

    // TODO do something with the table

    // just flushes outstanding commit, no futher cleanup needed, can be omitted.
    // HConnection holds no references to the returned HTable objects, they can be GC'd as soon as they leave scope.
    table.close();

    conn.close(); // done with the cluster, release resources

  }
  @Test
  public void put() throws IOException
  {
    // To add to a row, use Put. A Put constructor takes the name of the row
    // you want to insert into as a byte array. In HBase, the Bytes class
    // has utility for converting all kinds of java types to byte arrays. In
    // the below, we are converting the String "myLittleRow" into a byte
    // array to use as a row key for our get. Once you have a Put
    // instance, you can adorn it by setting the names of columns you want
    // to get on the row, the timestamp to use in your get, etc.
    // If no timestamp, the server applies current time to the edits.
    Put p = new Put(Bytes.toBytes("myLittleRow"));

    // To set the value you'd like to get in the row 'myLittleRow',
    // specify the column family, column qualifier, and value of the table
    // cell you'd like to get. The column family must already exist
    // in your table schema. The qualifier can be anything.
    // All must be specified as byte arrays as hbase is all about byte
    // arrays. Lets pretend the table 'myLittleHBaseTable' was created
    // with a family 'myLittleFamily'.
    p.add(Bytes.toBytes("myLittleFamily"), Bytes.toBytes("someQualifier"),
      Bytes.toBytes("Some Value"));

    // Once you've adorned your Put instance with all the updates you want
    // to make, to commit it do the following
    // (The HTable#put method takes the Put instance you've been building
    // and pushes the changes you made into hbase)
    table.put(p);
  }
  @Test
  public void get() throws IOException
  {
    // Now, to retrieve the data we just wrote. The values that come back
    // are Result instances. Generally, a Result is an object that will
    // package up the hbase return into the form you find most palatable.
    Get g = new Get(Bytes.toBytes("myLittleRow"));
    Result r = table.get(g);
    byte[] value = r.getValue(Bytes.toBytes("myLittleFamily"), Bytes
      .toBytes("someQualifier"));
    // If we convert the value bytes, we should get back 'Some Value', the
    // value we inserted at this location.
    String valueStr = Bytes.toString(value);
    System.out.println("GET: " + valueStr);
  }
  public void scan() throws IOException
  {
    // Sometimes, you won't know the row you're looking for. In this case,
    // you use a Scanner. This will give you cursor-like interface to the
    // contents of the table. To set up a Scanner, do like you did above
    // making a Put and a Get, create a Scan. Adorn it with column names,
    // etc.
    Scan s = new Scan();
    s.addColumn(Bytes.toBytes("myLittleFamily"), Bytes.toBytes("someQualifier"));
    ResultScanner scanner = table.getScanner(s);
    try
    {
      // Scanners return Result instances.
      // Now, for the actual iteration. One way is to use a while loop
      // like so:
      for (Result rr = scanner.next(); rr != null; rr = scanner.next())
      {
        // print out the row we found and the columns we were looking
        // for
        System.out.println("Found row: " + rr);
      }

      // The other approach is to use a foreach loop. Scanners are
      // iterable!
      // for (Result rr : scanner) {
      // System.out.println("Found row: " + rr);
      // }
    } finally
    {
      // Make sure you close your scanners when you are done!
      // Thats why we have it inside a try/finally clause
      scanner.close();
    }
  }
}
