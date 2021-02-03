![Qup logo](qup-logo.png)
---
**Qup:** (queue-up) A single-node job scheduler with NVIDIA GPU support
---

# Introduction
Qup is a simple, no-fuss single-node job scheduler, similar to slurm, sun grid engine, or pbs.  The primary features are:
- Out of the box, it keeps track of available CPU cores, memory, and GPU resources using a simple FIFO scheduler.
- It includes support for scheduling NVIDIA GPUs running CUDA (throught the CUDA_VISIBLE_DEVICES environment parameter)
- It's intended for single machines (i.e. your workstation, or your lab's server)
- It's intended to be setup and running in under two minutes. 
- It can parse a stripped-down subset of PBS scripts, making it somewhat compatible with slurm, SGE, or PBS.

Qup is written in Scala 2.11 and requires Java 1.8+.  It has been tested on Ubuntu 20.04 LTS, and is intended for Ubuntu (or systemd-based) installations.

# Why another job scheduler?
Using a job scheduler allows you to queue up a long list of jobs to run as the resources come available, either on your own workstation, or your lab's server.  The cannonical way of doing this is just for you to run your jobs manually, wait until they're finished, notice this, and then submit your next set of jobs.  This is very slow, causes lots of unused cycles, and (in multi-user settings) can cause jobs to consume more than the available resources.  For students, scientists, etc., a better way has typically been to install a job scheduler on your machine, queue it up with your jobs, let them run automatically as the resources come available.  

Slurm, Sun Grid Engine (SGE), and PBS are existing job schedulers that scale to large high-performance clusters.  Some of them (and their open source counterparts) are becoming infrequently maintained, making installation on modern Ubuntu distributions difficult.  Slurm is actively maintained, but can be challenging to install (and, is primarily intended for large HPCs).  Qup offers a simple single-system alternative, with similar looking tools and scripts (e.g. qsub, qstat, qdel, etc).

# Installation
Assuming Java 1.8+ is installed on your Ubuntu distribution, installation is fast, taking only a few moments.  Note that you should not install Qup if you already have slurm, SGE, or PBS installed, as it may overwrite some of your commands (qsub, qstat, qdel, etc): 

Clone the repository:
```
git clone https://www.github.com/clulab/qup.git
```

Run the installation script as root:
```
cd qup
sudo ./install.sh
```

Start the Qup service:
```
sudo systemctl start qup.service
```

# Getting Started -- Submitting your first job

The distribution includes a sample PBS script that runs *stress* (a high-CPU load stress tester) for 1 minute.  Here's how to submit it:

First, add yourself as a user:
```
sudo qadduser <yourusername> 1
```

Also, install *stress*:
```
sudo apt-get install stress
```

Then, submit the sample job:
```
qsub pbs-examples/example-1min.pbs
```

This should produce a verbose submission message, that looks something like this:
```
            Job ID:  1
             Status:  queued
          Exit Code:  -
           Priority:  1
        Preemptable:  false
           Username:  peter
           Job Name:  example_test_name
       Project Code:  example_project_name
       Working path:  /home/peter/github/qup1/qup
    Filename to run:  pbs-examples/example-1min.pbs
Resources requested:  cpucore: 4     gpu: 2     memory: 2
Resources allocated:  
    Output (stdout):  /home/peter/github/qup/job.1.stdout.txt
    Output (stderr):  /home/peter/github/qup/job.1.stderr.txt
    Submission Time:  2021-02-02 17:08:20
         Start Time:  
        Finish Time:  
 Time waited to run:  00d-00:00:00 so far
      Total runtime:  has not run yet.
    Wall Time Limit:  01d-00:00:00 (24.000 hours)
Notes: none 

Submitted job 1
```

You can then monitor the status of your job using **qstat**:
```
qstat
```

This should show the queue (as well as the last few completed jobs), as shown below.  The job status (first column) will be either **q** (queued), **r** (running), or **c** (completed).  In the example below, the job has already completed, with an exit code of 0 (success), and a total runtime of 00d-00:01:00 (1 minute):
```
Current Queue (0 jobs)
Available Resources: cpucore: 32/32  memory: 500/500  gpu: 4/4
ST  EC   PR  JOBID   USERNAME             PATH                           RUNSCRIPT                      RESOURCES                                          TIME                                                                   
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
c   0    1   1       peter                /home/peter/github/qup         pbs-examples/example-1min.pbs  cpucore: 4     gpu: 2     memory: 2                Ended: 2021-02-02 17:09:21 (Runtime: 00d-00:01:00)                     
```

The output of the job can be viewed using:
```
cat job.1.stdout.txt
cat job.1.stderr.txt
```




