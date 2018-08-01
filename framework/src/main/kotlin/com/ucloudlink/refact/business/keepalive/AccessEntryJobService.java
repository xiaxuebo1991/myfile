package com.ucloudlink.refact.business.keepalive;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.AsyncTask;

import com.ucloudlink.refact.utils.JLog;
import com.ucloudlink.refact.access.ui.AccessEntryService;

/**
 * Created by jianguo.he on 2017/6/21.
 */

public class AccessEntryJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        JLog.logi("Job, onStartJob ++");
        new AsyncTask<JobParameters, Integer, JobParameters>() {
            @Override
            protected JobParameters doInBackground(JobParameters... params) {
                JobSchedulerUtils.runService(AccessEntryJobService.this.getApplicationContext(), AccessEntryService.class);
                return params[0];
            }

            @Override
            protected void onPostExecute(JobParameters jobParameters) {
                jobFinished(jobParameters, false);
                JobSchedulerIntervalTimeCtrl.getInstance().setOnStartJobCount(JobSchedulerIntervalTimeCtrl.getInstance().getOnStartJobCount() + 1, "JobService-onStartJob()");
                JobSchedulerUtils.startJobScheduler(AccessEntryJobService.this.getApplicationContext()
                        , JobSchedulerCtrl.SWITCH_KEEP_LIVE, JobSchedulerIntervalTimeCtrl.getInstance().getIntervalMillis("JobService-onStartJob()"));
            }
        }.execute(params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        JLog.logi("Job, onStopJob --");
        return false;
    }

}
