import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/models/monitor_project.dart';
import 'package:hotel_monitor_app/services/report_history_service.dart';

void main() {
  test('新采集只保留当前结果作为上一次，并删除更早截图', () async {
    final directory = await Directory.systemTemp.createTemp('hotel_history_');
    addTearDown(() => directory.delete(recursive: true));
    final oldPrevious = File('${directory.path}/old.jpg')..writeAsStringSync('old');
    final oldCurrent = File('${directory.path}/current.jpg')
      ..writeAsStringSync('current');
    final project = MonitorProject(
      name: '测试项目',
      city: '成都',
      mainHotel: HotelConfig(
        id: 'main',
        meituanName: '美团酒店',
        ctripName: '携程酒店',
      ),
      competitors: [],
      ctripReports: {
        'main': HotelReportHistory(
          previous: ReportData(
            time: DateTime(2026, 7, 16),
            platform: '携程',
            hotelName: '携程酒店',
            roomNames: const [],
            screenshotPath: oldPrevious.path,
          ),
          current: ReportData(
            time: DateTime(2026, 7, 17),
            platform: '携程',
            hotelName: '携程酒店',
            roomNames: const [],
            screenshotPath: oldCurrent.path,
          ),
        ),
      },
    );

    await ReportHistoryService.beginCollection(project);

    final history = project.ctripReports['main']!;
    expect(history.current, isNull);
    expect(history.previous?.screenshotPath, oldCurrent.path);
    expect(await oldCurrent.exists(), isTrue);
    expect(await oldPrevious.exists(), isFalse);
  });
}
