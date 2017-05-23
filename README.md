# push-notification-scheduler

[![Build Status](https://travis-ci.org/hmrc/push-notification-scheduler.svg)](https://travis-ci.org/hmrc/push-notification-scheduler) [ ![Download](https://api.bintray.com/packages/hmrc/releases/push-notification-scheduler/images/download.svg) ](https://bintray.com/hmrc/releases/push-notification-scheduler/_latestVersion)

The Push Notification Scheduler manages the mobile application push notifications processes:

* Retrieves "incomplete" registrations (ie. registrations without an AWS Endpoint ARN) from the Push Registration service and passes them to the SNS Client service to get the associated endpoint ARN
* Retrieves unsent (as well as previously failed) notifications from the Push Notification service and forwards them to the SNS Client to be delivered to the relevant handset(s)

The frequency with which the tasks are run, and the number of actors dedicated to each task, is configured in `scheduling` and `throttling` sections in `application.conf`.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")