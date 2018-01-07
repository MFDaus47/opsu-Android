package fluddokt.opsu.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import fluddokt.ex.DeviceInfo;
import fluddokt.ex.DynamoDB.DynamoDB;
import fluddokt.ex.InterstitialAdLoader;
import fluddokt.ex.RewardVideoAdLoader;
import fluddokt.opsu.fake.File;
import fluddokt.opsu.fake.GameOpsu;

import static android.R.attr.x;

public class AndroidLauncher extends AndroidApplication {
	final String identityPool="us-east-1:db541cf4-1b41-4045-b60f-adeaa6b9cfeb";
	final String REWARD_VIDEO_ID="ca-app-pub-4238071175751641/9918175716";
	private InterstitialAd mInterstitialAd;
//	private RewardedVideoAd mRewardedVideoAd;
	private ExecutorService executorService;
	private SharedPreferences prefs;
	private SharedPreferences.Editor editor;
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useImmersiveMode = true;
		config.useWakelock = true;
		executorService= Executors.newSingleThreadExecutor();
		prefs= PreferenceManager.getDefaultSharedPreferences(this);
		editor=prefs.edit();
		DeviceInfo.info = new DeviceInfo() {
			@Override
			public String getInfo() {

				return
						"BOARD: "+Build.BOARD
						+"\nFINGERPRINT: "+Build.FINGERPRINT
						+"\nHOST: "+Build.HOST
						+"\nMODEL: "+Build.MODEL
						+"\nINCREMENTAL: "+Build.VERSION.INCREMENTAL
						+"\nRELEASE: "+Build.VERSION.RELEASE
						+"\n"
						;
			}

			@Override
			public File getDownloadDir() {

				return new File(String.valueOf(new FileHandle(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))));
			}
			@Override
			public String getAndroidVersion(){
				double release=Double.parseDouble(Build.VERSION.RELEASE.replaceAll("(\\d+[.]\\d+)(.*)","$1"));
				String codeName="Unsupported";//below Jelly bean OR above Oreo
				if(release>=4.1 && release<4.4)codeName="Jelly Bean";
				else if(release<5)codeName="Kit Kat";
				else if(release<6)codeName="Lollipop";
				else if(release<7)codeName="Marshmallow";
				else if(release<8)codeName="Nougat";
				else if(release<9)codeName="Oreo";
				return codeName+" v"+release+", API Level: "+Build.VERSION.SDK_INT;
			}

		};
		DynamoDB.database = new DynamoDB(){
			private CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
					getApplicationContext(),
					identityPool, // Identity pool ID
					Regions.US_EAST_1 // Region
			);
			@Override
			public CognitoCachingCredentialsProvider retrieveCredentials(){
				return credentialsProvider;
			}

		};

//
//		final AlertDialog alertDialog=new AlertDialog.Builder(getApplicationContext())
//				.setTitle("Reward Video")
//				.setMessage("Would you like to watch a video for 15 minutes ad-free?")
//				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//					@Override
//					public void onClick(DialogInterface dialog, int which) {
//						RewardVideoAdLoader.ad.loadAndShow();
//					}
//				}).setNegativeButton("No", new DialogInterface.OnClickListener() {
//			@Override
//			public void onClick(DialogInterface dialogInterface, int i) {
//				InterstitialAdLoader.ad.loadAndShow();
//			}
//		}).create();
		//Initialize interstitial ads
		MobileAds.initialize(this,
				"ca-app-pub-4238071175751641~8789389898");
		//mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this);
		//resetVideo();
		mInterstitialAd = new InterstitialAd(this);
		mInterstitialAd.setAdUnitId("ca-app-pub-4238071175751641/8635925779");
		//Send failure message if ad fails to load
		mInterstitialAd.setAdListener(new AdListener(){
			@Override
			public void onAdFailedToLoad(int errorCode){
				InterstitialAdLoader.ad.sendNotification("Connection Failed");
			}
		});
		InterstitialAdLoader.ad = new InterstitialAdLoader(){
			//Load ad
			@Override
			public void load(){
				try {
					runOnUiThread(new Runnable() {
						public void run() {
							if(!mInterstitialAd.isLoaded()&&!mInterstitialAd.isLoading()){
							AdRequest interstitialRequest = new AdRequest.Builder().build();
							mInterstitialAd.loadAd(interstitialRequest);
							}
						}
					});
				}
				catch (Exception e) {}

			}
			@Override
			public void loadAndShow(){
				try {
					runOnUiThread(new Runnable() {
						public void run() {
							//If the ad isn't loaded, load it, and then show it immediately after its loaded
							if(!mInterstitialAd.isLoaded()) {
								if (!mInterstitialAd.isLoading()) {
									AdRequest interstitialRequest = new AdRequest.Builder().build();
									mInterstitialAd.loadAd(interstitialRequest);
									InterstitialAdLoader.ad.sendNotification("Loading...");
								}
								//Show the ad immediately after its loaded
								mInterstitialAd.setAdListener(new AdListener() {
									@Override
									public void onAdLoaded() {
										mInterstitialAd.show();
										//Reset the ad so it doesn't always immediately show the ad when its done loading
										mInterstitialAd.setAdListener(new AdListener() {
											@Override
											public void onAdLoaded() {
												super.onAdLoaded();
											}
										});
									}
								});
							}
							//If the ad is loaded show the add
							else
								mInterstitialAd.show();
						}
					});
				}
				catch (Exception e) {}
			}
			//Sets the action that occurs when the ad is closed
			@Override
			public void onShowCompleted(final Callable<Boolean> c){
				try {
					runOnUiThread(new Runnable() {
						public void run() {
							mInterstitialAd.setAdListener(new AdListener(){
								@Override
								public void onAdClosed() {
									super.onAdClosed();
									execute(c);
									AdRequest interstitialRequest = new AdRequest.Builder().build();
									mInterstitialAd.loadAd(interstitialRequest);
								}
							});
						}
					});
				}
				catch (Exception e) {}
			}
			};
//		RewardVideoAdLoader.ad = new RewardVideoAdLoader(){
//			@Override
//			public void init(){
//				RewardVideoAdLoader.ad.setLastAdWatched(prefs.getLong("lastAd",Long.MIN_VALUE));
//			}
//			//Check whether or not the user is within their 30 min ad free time
//			@Override
//			public void showAds(){
//				if(RewardVideoAdLoader.ad.getLastAdWatched()-new Date().getTime()<1800000)
//					return;
//				else{
//					try {
//						runOnUiThread(new Runnable() {
//							public void run() {
//								alertDialog.show();
//							}
//						});
//					}catch (Exception e){}
//
//				}
//			}
//			//Load ad
//			@Override
//			public void load(){
//				try {
//					runOnUiThread(new Runnable() {
//						public void run() {
//							if(!mRewardedVideoAd.isLoaded())
//								mRewardedVideoAd.loadAd(REWARD_VIDEO_ID, new AdRequest.Builder().build());
//						}
//					});
//				}
//				catch (Exception e) {}
//
//			}
//			@Override
//			public void loadAndShow(){
//				try {
//					runOnUiThread(new Runnable() {
//						public void run() {
//							//If the ad isn't loaded, load it, and then show it immediately after its loaded
//							if(!mRewardedVideoAd.isLoaded()){
//								mRewardedVideoAd.loadAd(REWARD_VIDEO_ID, new AdRequest.Builder().build());
//								//Show the ad immediately after its loaded
//								showVideoWhenLoaded();
//								resetVideo();
//							}
//							//If the ad is loaded show the add
//							else
//								mRewardedVideoAd.show();
//						}
//					});
//				}
//				catch (Exception e) {}
//			}
//			//Sets the action that occurs when the ad is closed
//
//		};

		initialize(new GameOpsu(), config);

	}
	//Execute a callable with a 1.5s time limit
	private boolean execute(Callable<Boolean> c){
		Future<Boolean> task = executorService.submit(c);
		try {
			return task.get(1500, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			return false;
		}
	}
//	private void resetVideo(){
//		mRewardedVideoAd.setRewardedVideoAdListener(new RewardedVideoAdListener() {
//			@Override
//			public void onRewardedVideoAdLoaded() {}
//			@Override
//			public void onRewardedVideoAdOpened() {}
//			@Override
//			public void onRewardedVideoStarted() {}
//			@Override
//			public void onRewardedVideoAdClosed() {}
//			@Override
//			public void onRewarded(RewardItem rewardItem) {editor.putLong("lastAd", new Date().getTime());}
//			@Override
//			public void onRewardedVideoAdLeftApplication() {}
//			@Override
//			public void onRewardedVideoAdFailedToLoad(int i) {RewardVideoAdLoader.ad.sendNotification("Connection Failed");}
//		});
//	}
//	private void showVideoWhenLoaded(){
//		mRewardedVideoAd.setRewardedVideoAdListener(new RewardedVideoAdListener() {
//			@Override
//			public void onRewardedVideoAdLoaded() {mRewardedVideoAd.show();}
//			@Override
//			public void onRewardedVideoAdOpened() {}
//			@Override
//			public void onRewardedVideoStarted() {}
//			@Override
//			public void onRewardedVideoAdClosed() {}
//			@Override
//			public void onRewarded(RewardItem rewardItem) {editor.putLong("lastAd", new Date().getTime());}
//			@Override
//			public void onRewardedVideoAdLeftApplication() {}
//			@Override
//			public void onRewardedVideoAdFailedToLoad(int i) {RewardVideoAdLoader.ad.sendNotification("Connection Failed");}
//		});
//	}

}
