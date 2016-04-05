package com.hippo.dict;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.hippo.util.TextUrl;

import java.util.ArrayList;
import java.util.List;

public class DictImportService extends Service {
    private static final String TAG = "DictImportService";
    private static final boolean DEBUG = false;

    private DictManager mDictManager;
    private final List<ProcessListener> mListeners = new ArrayList<>();
    private ProcessListener mDictProcessListener;
    private AsyncTask mImportAsyncTask;
    private DictNotification mDictNotification;

    // current task information
    private int mItemNum;
    private Uri mDictUri;
    private boolean mRunningFlag = false;

    public DictImportService() {}

    private final Binder serviceBinder = new DictImportServiceBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDictManager = new DictManager(this);
        mDictNotification = new DictNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return super.onStartCommand(intent, flags, startId);
    }

    public interface ProcessListener {
        void process(int progress);

        void processTotal(int total);

        void processComplete();
    }

    public class DictImportServiceBinder extends Binder {
        public DictImportService getService() {
            return DictImportService.this;
        }
    }

    public void importDict(Uri dictUri) {
        if (dictUri == null) {
            // TODO error tip
            if (DEBUG) {
                Log.e(TAG, "[importDict] dictUri is null");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "[importDict] start import async task");
        }
        mImportAsyncTask = new ImportAsyncTask(dictUri).execute();
    }

    public void abortImport() {
        if (mImportAsyncTask == null) {
            if (DEBUG) {
                Log.e(TAG, "[abortImport] mImportAsyncTask is null");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "[abortImport] import abort");
        }

        // fixme
        // this i just set a flag to abort the parse thread,it may cause a exception
        // it there may be a elegant way to shut the worker thread down
        mDictManager.importAbort();

        // just for service go die
        mImportAsyncTask.cancel(true);
    }

    public Uri getUri() {
        return mDictUri;
    }

    public int getItemNum() {
        return mItemNum;
    }

    public boolean isRunning() {
        return mRunningFlag;
    }

    public void setOnProgressListener(ProcessListener onProgressListener) {
        if (onProgressListener != null) {
            mListeners.add(onProgressListener);
            mListeners.remove(mDictNotification.mNotificationListener);
            mDictNotification.stopNotify();
        }
    }

    public void removeOnProgressListener(ProcessListener onProgressListener) {
        if (onProgressListener != null) {
            mListeners.remove(onProgressListener);
        }

        // if there is no listener in listener list,we is in the background mostly
        // we post the process progress information to a notification
        if (mListeners.size() == 0 && null != mDictUri) {
            mDictNotification.setMax(mItemNum);
            mDictNotification.setFileName(TextUrl.getFileName(mDictUri.toString()));
            mListeners.add(mDictNotification.mNotificationListener);
        }
    }

    class ImportAsyncTask extends AsyncTask<Void, Integer, Void> {

        public ImportAsyncTask(Uri dictUri) {
            mDictUri = dictUri;
            mRunningFlag = true;
            mDictProcessListener = new ProcessListener() {
                @Override
                public void process(int progress) {
                    if (DEBUG) {
                        Log.d(TAG, "[process] progress:" + progress);
                    }
                    publishProgress(progress);
                }

                @Override
                public void processTotal(int total) {
                    if (DEBUG) {
                        Log.d(TAG, "process total " + total);
                    }
                    mItemNum = total;
                    for (ProcessListener listener : mListeners) {
                        listener.processTotal(total);
                    }
                }

                @Override
                public void processComplete() {
                    // we don't do any thing here,let async task handle this
                    if (DEBUG) {
                        Log.d(TAG, "processComplete");
                    }
                }

            };
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                mDictManager.importDict(mDictUri, mDictProcessListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);

            if (DEBUG) {
                Log.d(TAG, "[onProgressUpdate] process item " + progress[0]);
            }
            for (ProcessListener listener : mListeners) {
                listener.process(progress[0]);
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            for (ProcessListener listener : mListeners) {
                listener.processComplete();
            }
            mRunningFlag = false;
            DictImportService.this.goDie();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            DictImportService.this.goDie();
        }
    }

    public void goDie() {
        if (DEBUG) {
            Log.d(TAG, "[goDie] task done or abort,go to die");
        }
        this.stopSelf();
    }
}
