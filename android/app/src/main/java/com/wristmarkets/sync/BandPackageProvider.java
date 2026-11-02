package com.wristmarkets.sync;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
public class BandPackageProvider extends ContentProvider {
  @Override public boolean onCreate(){return true;}
  private void check(Uri uri){if(!"/band.rpk".equals(uri.getPath()))throw new IllegalArgumentException("Unknown package");}
  private File file()throws IOException{
    File result=new File(getContext().getCacheDir(),"WristMarkets-Band-0.3.0.rpk");
    if(!result.exists())try(InputStream in=getContext().getAssets().open("band.rpk");OutputStream out=new FileOutputStream(result)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    return result;
  }
  @Override public String getType(Uri u){check(u);return "application/zip";}
  @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){check(u);MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});try{cursor.addRow(new Object[]{"WristMarkets-Band-0.3.0.rpk",file().length()});}catch(IOException e){return cursor;}return cursor;}
  @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{check(u);if(!"r".equals(mode))throw new FileNotFoundException("Read only");try{return ParcelFileDescriptor.open(file(),ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException e){throw new FileNotFoundException("Package unavailable");}}
  @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
  @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
