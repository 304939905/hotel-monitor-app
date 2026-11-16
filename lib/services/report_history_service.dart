import 'dart:io';

import '../models/monitor_project.dart';

class ReportHistoryService {
  const ReportHistoryService._();

  /// Starts one new collection for a project.
  ///
  /// The former current result becomes the only retained previous result. Any
  /// screenshot older than that is deleted from app storage.
  static Future<void> beginCollection(MonitorProject project) async {
    final obsoletePaths = <String>{};

    void rotate(Map<String, HotelReportHistory> reports) {
      for (final history in reports.values) {
        final oldPreviousPath = history.previous?.screenshotPath?.trim();
        final currentPath = history.current?.screenshotPath?.trim();
        if (oldPreviousPath != null &&
            oldPreviousPath.isNotEmpty &&
            oldPreviousPath != currentPath) {
          obsoletePaths.add(oldPreviousPath);
        }
        history.previous = history.current;
        history.current = null;
      }
    }

    rotate(project.ctripReports);
    rotate(project.meituanReports);

    for (final path in obsoletePaths) {
      try {
        final file = File(path);
        if (await file.exists()) await file.delete();
      } on FileSystemException {
        // A missing or locked obsolete image must not prevent a new capture.
      }
    }
  }
}
