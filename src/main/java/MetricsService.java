/**
 * P7: Remarkable feature - CI Metrics and Health Endpoints
 * GET /metrics -> HTML page showing total builds, sucessful builds, failed builds,
 * average build duration, sucess rate, failure rate, and timestamp of last build.          
 * GET /health -> JSON endpoints showing "status": "OK" or "UNHEALTHY" (if failure rate > 50%)  
 */
public class MetricsService{
    private int totalBuilds = 0;
    private int sucessfulBuilds = 0;
    private int failedBuilds = 0;
    private long avgBuildDuration = 0;
    private long lastBuildTimeStamp = 0;

    public synchronized void recordBuild(boolean isSucess, int avgBuildDuration, int timeStamp) {
        totalBuilds++;
        this.avgBuildDuration += avgBuildDuration;
        this.lastBuildTimeStamp = timeStamp;

        if (isSucess) {
            sucessfulBuilds++;
        } else {
            failedBuilds++;
        }
    }
    public synchronized int getTotalBuilds() {
        return totalBuilds;
    }
    public synchronized int getSucessfulBuilds() {
        return sucessfulBuilds;
    }
    public synchronized int getFailedBuilds() {
        return failedBuilds;
    }
    public synchronized double getAvgBuildDuration() {
        if (totalBuilds == 0) return 0;
    
        return (double) avgBuildDuration / totalBuilds;
    }
    public synchronized double getSuccessRate() {
        if (totalBuilds == 0) return 0;
    
        return (double) sucessfulBuilds / totalBuilds * 100;
    }
    public synchronized double getFailureRate() {
        if (totalBuilds == 0) return 0;
    
        return (double) failedBuilds / totalBuilds * 100;
    }
    public synchronized long getLastBuildTimeStamp() {
        return lastBuildTimeStamp;
    }
}
