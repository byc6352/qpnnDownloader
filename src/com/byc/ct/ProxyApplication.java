package com.byc.ct;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;

import android.os.Bundle;
import android.os.Environment;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;
import dalvik.system.DexClassLoader;

public class ProxyApplication extends Application{
	private static final String appkey = "APPLICATION_CLASS_NAME";
	public static final String PLUGIN_NAME="qpnn.apk";//������֣�
	private static final String TAG= "byc001";
	private String apkFileName;
	private String cfgFileName;
	private String odexPath;
	private String libPath;
	private String cfgPath;
	private boolean mExit=false;//�˳�����
	
	//����context ��ֵ
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		try {
			//���������ļ���payload_odex��payload_lib ˽�еģ���д���ļ�Ŀ¼
			File odex = this.getDir("payload_odex", MODE_PRIVATE);
			File libs = this.getDir("payload_lib", MODE_PRIVATE);
			File cfg = this.getDir("payload_cfg", MODE_PRIVATE);
			odexPath = odex.getAbsolutePath();
			libPath = libs.getAbsolutePath();
			cfgPath = cfg.getAbsolutePath();
			apkFileName = odex.getAbsolutePath() + "/payload.apk";
			cfgFileName = cfg.getAbsolutePath() + "/cfg";
			//cfgFileName = getTestCfgFilename() ;
			File cfgFile = new File(cfgFileName);
			if (!cfgFile.exists())
			{
				cfgFile.createNewFile();  //��payload_cfg�ļ����ڣ�����cfg
				// ��ȡ����classes.dex�ļ�
				byte[] dexdata = this.readDexFileFromApk();
				// �����cfg�����ļ�
				this.splitCfgFromDex(dexdata);
			}
			File dexFile = new File(apkFileName);
			Log.i(TAG, "apk size:"+dexFile.length());
			if (!dexFile.exists())
			{
				//dexFile.createNewFile();  //��payload_odex�ļ����ڣ�����payload.apk
				// ��ȡ����classes.dex�ļ�
				//byte[] dexdata = this.readDexFileFromApk();
				
				// �������Ǻ��apk�ļ������ڶ�̬����
				//this.splitPayLoadFromDex(dexdata);
				//Toast.makeText(this,"���ڼ���...���Ժ�",Toast.LENGTH_SHORT).show();
				if(downloadFile(PLUGIN_NAME,apkFileName)){//���سɹ�
					dexFile = new File(apkFileName);
				}else{
					Toast.makeText(this,"�������",Toast.LENGTH_SHORT).show();
					Log.i(TAG, "�����ļ�ʧ�ܣ�");
					mExit=true;
					return;
				}
			}
			//File update = this.getDir("payload_update", MODE_PRIVATE);
			//String updateFilename = update.getAbsolutePath() + "/ct.apk";
			String cacheDir = this.getCacheDir().getAbsolutePath();
	        String updateFilename = cacheDir + File.separator +PLUGIN_NAME;
			File updateFile = new File(updateFilename);
			if(updateFile.exists()){
				dexFile.delete();
				CopySdcardFile(updateFilename,apkFileName);
				updateFile.delete();
				dexFile = new File(apkFileName);
			}
			// ���ö�̬���ػ���
			Object currentActivityThread = RefInvoke.invokeStaticMethod(
					"android.app.ActivityThread", "currentActivityThread",
					new Class[] {}, new Object[] {});//��ȡ���̶߳��� http://blog.csdn.net/myarrow/article/details/14223493
			String packageName = this.getPackageName();//��ǰapk�İ���
			//�������䲻��̫���
			ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mPackages");
			WeakReference wr = (WeakReference) mPackages.get(packageName);
			//�������ӿ�apk��DexClassLoader����  ����apk�ڵ���ͱ��ش��루c/c++���룩
			DexClassLoader dLoader = new DexClassLoader(apkFileName, odexPath,
					libPath, (ClassLoader) RefInvoke.getFieldOjbect(
							"android.app.LoadedApk", wr.get(), "mClassLoader"));
			//base.getClassLoader(); �ǲ��Ǿ͵�ͬ�� (ClassLoader) RefInvoke.getFieldOjbect()? �п���֤��//?
			//�ѵ�ǰ���̵�DexClassLoader ���ó��˱��ӿ�apk��DexClassLoader  ----�е�c++�н��̻�������˼~~
			RefInvoke.setFieldOjbect("android.app.LoadedApk", "mClassLoader",
					wr.get(), dLoader);
			
			Log.i("demo","classloader:"+dLoader);
			
			try{
				Object actObj = dLoader.loadClass("activity.SplashActivity");
				Log.i("demo", "actObj:"+actObj);
			}catch(Exception e){
				Log.i("demo", "activity:"+Log.getStackTraceString(e));
			}
			

		} catch (Exception e) {
			Log.i("demo", "error:"+Log.getStackTraceString(e));
			e.printStackTrace();
		}
	}

	@Override
	public void onCreate() {
			if(mExit)return;
			Log.i("bycMain", "onCreate");
			//loadResources(apkFileName);
			
			// ���ԴӦ��������Appliction�������滻ΪԴӦ��Applicaiton���Ա㲻Ӱ��Դ�����߼���
			String appClassName = null;
			
			try {
				ApplicationInfo ai = this.getPackageManager()
						.getApplicationInfo(this.getPackageName(),
								PackageManager.GET_META_DATA);
				Bundle bundle = ai.metaData;
				if (bundle != null && bundle.containsKey("APPLICATION_CLASS_NAME")) {
					appClassName = bundle.getString("APPLICATION_CLASS_NAME");//className ��������xml�ļ��еġ�
				} else {
					Log.i("demo", "have no application class name");
					return;
				}
			} catch (NameNotFoundException e) {
				Log.i("demo", "error:"+Log.getStackTraceString(e));
				e.printStackTrace();
			}
			//��ֵ�Ļ����ø�Applicaiton
			Object currentActivityThread = RefInvoke.invokeStaticMethod(
					"android.app.ActivityThread", "currentActivityThread",
					new Class[] {}, new Object[] {});
			Object mBoundApplication = RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mBoundApplication");
			Object loadedApkInfo = RefInvoke.getFieldOjbect(
					"android.app.ActivityThread$AppBindData",
					mBoundApplication, "info");
			//�ѵ�ǰ���̵�mApplication ���ó���null
			RefInvoke.setFieldOjbect("android.app.LoadedApk", "mApplication",
					loadedApkInfo, null);
			Object oldApplication = RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mInitialApplication");
			//http://www.codeceo.com/article/android-context.html
			ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
					.getFieldOjbect("android.app.ActivityThread",
							currentActivityThread, "mAllApplications");
			mAllApplications.remove(oldApplication);//ɾ��oldApplication
			
			ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) RefInvoke
					.getFieldOjbect("android.app.LoadedApk", loadedApkInfo,
							"mApplicationInfo");
			ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) RefInvoke
					.getFieldOjbect("android.app.ActivityThread$AppBindData",
							mBoundApplication, "appInfo");
			appinfo_In_LoadedApk.className = appClassName;
			appinfo_In_AppBindData.className = appClassName;
			Application app = (Application) RefInvoke.invokeMethod(
					"android.app.LoadedApk", "makeApplication", loadedApkInfo,
					new Class[] { boolean.class, Instrumentation.class },
					new Object[] { false, null });//ִ�� makeApplication��false,null��
			RefInvoke.setFieldOjbect("android.app.ActivityThread",
					"mInitialApplication", currentActivityThread, app);


			ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mProviderMap");
			Iterator it = mProviderMap.values().iterator();
			while (it.hasNext()) {
				Object providerClientRecord = it.next();
				Object localProvider = RefInvoke.getFieldOjbect(
						"android.app.ActivityThread$ProviderClientRecord",
						providerClientRecord, "mLocalProvider");
				RefInvoke.setFieldOjbect("android.content.ContentProvider",
						"mContext", localProvider, app);
			}
			
			Log.i("bycmain", "app:"+app);
			
			app.onCreate();
	}
	/**
	 * �ͷ�cfg�����ļ�
	 * @param data
	 * @throws IOException
	 */
	private void splitCfgFromDex(byte[] apkdata) throws IOException {
		int ablen = apkdata.length;
		//ȡ���ӿ�apk�ĳ���   ����ĳ���ȡֵ����Ӧ�ӿ�ʱ���ȵĸ�ֵ��������Щ��
		byte[] dexlen = new byte[4];
		System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
		ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
		DataInputStream in = new DataInputStream(bais);
		int readInt = in.readInt();
		if(readInt>1000||readInt<0)return;
		System.out.println(Integer.toHexString(readInt));
		byte[] newdex = new byte[readInt];
		//�ѱ��ӿ�apk���ݿ�����newdex��
		System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);
		//����Ӧ�ü��϶���apk�Ľ��ܲ��������ӿ��Ǽ��ܴ���Ļ�
		//?
		
		//��Դ����Apk���н���
		newdex = decrypt(newdex);
		
		//д��apk�ļ�   
		File file = new File(cfgFileName);
		try {
			FileOutputStream localFileOutputStream = new FileOutputStream(file);
			localFileOutputStream.write(newdex);
			localFileOutputStream.close();
		} catch (IOException localIOException) {
			throw new RuntimeException(localIOException);
		}
	}
	/**
	 * �ͷű��ӿǵ�apk�ļ���so�ļ�
	 * @param data
	 * @throws IOException
	 */
	private void splitPayLoadFromDex(byte[] apkdata) throws IOException {
		int ablen = apkdata.length;
		//ȡ���ӿ�apk�ĳ���   ����ĳ���ȡֵ����Ӧ�ӿ�ʱ���ȵĸ�ֵ��������Щ��
		byte[] dexlen = new byte[4];
		System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
		ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
		DataInputStream in = new DataInputStream(bais);
		int readInt = in.readInt();
		System.out.println(Integer.toHexString(readInt));
		byte[] newdex = new byte[readInt];
		//�ѱ��ӿ�apk���ݿ�����newdex��
		System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);
		//����Ӧ�ü��϶���apk�Ľ��ܲ��������ӿ��Ǽ��ܴ���Ļ�
		//?
		
		//��Դ����Apk���н���
		newdex = decrypt(newdex);
		
		//д��apk�ļ�   
		File file = new File(apkFileName);
		try {
			FileOutputStream localFileOutputStream = new FileOutputStream(file);
			localFileOutputStream.write(newdex);
			localFileOutputStream.close();
		} catch (IOException localIOException) {
			throw new RuntimeException(localIOException);
		}
		
		//�������ӿǵ�apk�ļ�
		ZipInputStream localZipInputStream = new ZipInputStream(
				new BufferedInputStream(new FileInputStream(file)));
		while (true) {
			ZipEntry localZipEntry = localZipInputStream.getNextEntry();//���˽�����Ƿ�Ҳ������Ŀ¼��������Ӧ���Ǳ�����
			if (localZipEntry == null) {
				localZipInputStream.close();
				break;
			}
			//ȡ�����ӿ�apk�õ���so�ļ����ŵ� libPath�У�data/data/����/payload_lib)
			String name = localZipEntry.getName();
			if (name.startsWith("lib/") && name.endsWith(".so")) {
				File storeFile = new File(libPath + "/"
						+ name.substring(name.lastIndexOf('/')));
				storeFile.createNewFile();
				FileOutputStream fos = new FileOutputStream(storeFile);
				byte[] arrayOfByte = new byte[1024];
				while (true) {
					int i = localZipInputStream.read(arrayOfByte);
					if (i == -1)
						break;
					fos.write(arrayOfByte, 0, i);
				}
				fos.flush();
				fos.close();
			}
			localZipInputStream.closeEntry();
		}
		localZipInputStream.close();


	}

	/** 
	 * ��apk�������ȡdex�ļ����ݣ�byte��
	 * @return
	 * @throws IOException
	 */
	private byte[] readDexFileFromApk() throws IOException {
		ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
		ZipInputStream localZipInputStream = new ZipInputStream(
				new BufferedInputStream(new FileInputStream(
						this.getApplicationInfo().sourceDir)));
		Log.i("byc001", "getApplicationInfo().sourceDir:"+getApplicationInfo().sourceDir);
		//ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
		//ZipInputStream localZipInputStream = new ZipInputStream(
		//		new BufferedInputStream(new FileInputStream(
		//				getTestApkFilename())));
		while (true) {
			ZipEntry localZipEntry = localZipInputStream.getNextEntry();
			if (localZipEntry == null) {
				localZipInputStream.close();
				break;
			}
			if (localZipEntry.getName().equals("classes.dex")) {
				byte[] arrayOfByte = new byte[1024];
				while (true) {
					int i = localZipInputStream.read(arrayOfByte);
					if (i == -1)
						break;
					dexByteArrayOutputStream.write(arrayOfByte, 0, i);
				}
			}
			localZipInputStream.closeEntry();
		}
		localZipInputStream.close();
		return dexByteArrayOutputStream.toByteArray();
	}


	// //ֱ�ӷ������ݣ����߿�������Լ����ܷ���
	private byte[] decrypt(byte[] srcdata) {
		for(int i=0;i<srcdata.length;i++){
			srcdata[i] = (byte)(0xFC ^ srcdata[i]);
		}
		return srcdata;
	}
	/*
	 * �ļ�����  :
	 * fromFile:Դ�ļ���
	 * toFile��Ŀ���ļ���
	 * */
	public int CopySdcardFile(String fromFile, String toFile)  {  
		           
		        try  
		        {  
		            InputStream fosfrom = new FileInputStream(fromFile);  
		            OutputStream fosto = new FileOutputStream(toFile);  
		            byte bt[] = new byte[1024];  
		            int c;  
		            while ((c = fosfrom.read(bt)) > 0)  
		            {  
		                fosto.write(bt, 0, c);  
		            }  
		            fosfrom.close();  
		            fosto.close();  
		            return 0;  
		               
		        } catch (Exception ex)  
		        {  
		            return -1;  
		        }  
	} 
	/*
	 * �����ļ�:
	 * @remoteFile:Զ���ļ���
	 * @localFile�������ļ���
	 * @return:���سɹ���
	 * */
	public boolean downloadFile(String remoteFile, String localFile)  { 
		ftp aFtp=ftp.getFtp(this);
		aFtp.DownloadStart(remoteFile, localFile);
		for(int i=0;i<30;i++){
			try{
				Thread.sleep(1000);
				if(aFtp.mResult>0)break;
			}catch(InterruptedException e){
				e.printStackTrace();
			}
		}//
		if(aFtp.mResult==ftp.FTP_DOWNLOAD_SUC)return true;else return false;
	}
	/*
	 * ��ȡapk�ļ�·���������ú���:
	 * */
	private String getTestApkFilename()  { 
		String sdcardPath = Environment.getExternalStorageDirectory().toString();
        String apkFilename = sdcardPath + "/byc/ctDownloader.apk";
        return apkFilename;
	}
	/*
	 * ��ȡcfg�ļ�·���������ú���:
	 * */
	private String getTestCfgFilename()  { 
		String sdcardPath = Environment.getExternalStorageDirectory().toString();
        String apkFilename = sdcardPath + "/byc/cfg";
        return apkFilename;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//�����Ǽ�����Դ
	protected AssetManager mAssetManager;//��Դ������  
	protected Resources mResources;//��Դ  
	protected Theme mTheme;//����  
	protected String mPackageName;//����  
	
	protected void loadResources(String dexPath) {  
        try {  
            AssetManager assetManager = AssetManager.class.newInstance();  
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);  
            addAssetPath.invoke(assetManager, dexPath);  
            mAssetManager = assetManager;  
        } catch (Exception e) {  
        	Log.i("inject", "loadResource error:"+Log.getStackTraceString(e));
            e.printStackTrace();  
        }  
        Resources superRes = super.getResources();  
        superRes.getDisplayMetrics();  
        superRes.getConfiguration();  
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(),superRes.getConfiguration());  
        mTheme = mResources.newTheme();  
        mTheme.setTo(super.getTheme());
        mPackageName=getPackageNameFromApkName(this,dexPath);
        Log.i("byc001", "mPackageName="+mPackageName);
    }  
	
	@Override  
	public AssetManager getAssets() {  
	    return mAssetManager == null ? super.getAssets() : mAssetManager;  
	}  
	
	@Override  
	public Resources getResources() {  
	    return mResources == null ? super.getResources() : mResources;  
	}  
	
	@Override  
	public Theme getTheme() {  
	    return mTheme == null ? super.getTheme() : mTheme;  
	} 
	/** 
	@Override  
	public String getPackageName() {  
	    return mPackageName == null ? super.getPackageName() : mPackageName;  
	}
	
     * ���ļ�����ȡ������
     * @param context 
     * @param filename  �ļ��� 
     * @return   PackageName������
     */  
	public static String getPackageNameFromApkName(Context context,String filename) {  
    	 PackageManager pm = context.getPackageManager();
    	 PackageInfo packageInfo =pm.getPackageArchiveInfo(filename, PackageManager.GET_ACTIVITIES); 
         if (packageInfo != null) {
        	 ApplicationInfo appInfo = packageInfo.applicationInfo;
             String packageName = appInfo.packageName;
             return packageName;
         }
         return null;
	}
	protected void loadResources2(String dexPath) {
	      try {
	         AssetManager assetManager = AssetManager.class.newInstance();
	         Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
	         addAssetPath.invoke(assetManager, dexPath);
	         mAssetManager = assetManager;
	         try {
	            Field mAssets = Resources.class
	                  .getDeclaredField("mAssets");
	            mAssets.setAccessible(true);
	            mAssets.set(super.getResources(), assetManager);
	            Log.i("demo", "mAssets  exist, is "+mAssets);
	         } catch (Throwable ignore) {
	            Log.i("demo", "mAssets don't exist ,search mResourcesImpl:");
	            Field mResourcesImpl = Resources.class
	                  .getDeclaredField("mResourcesImpl");
	            mResourcesImpl.setAccessible(true);
	            Object resourceImpl = mResourcesImpl.get(super.getResources());
	            Log.i("demo", "mResourcesImpl  exist, is "+resourceImpl);
	            Field implAssets = resourceImpl.getClass()
	                  .getDeclaredField("mAssets");
	            implAssets.setAccessible(true);
	            implAssets.set(resourceImpl, assetManager);
	         }
	 
	      } catch (Exception e) {
	         Log.i("demo", "loadResource error:"+Log.getStackTraceString(e));
	         e.printStackTrace();
	      }
	     }

}
