// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/main.dart';
import 'package:hotel_monitor_app/models/monitor_project.dart';
import 'package:hotel_monitor_app/services/project_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('首次打开显示示例监控项目', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const HotelMonitorApp());
    await tester.pumpAndSettle();
    expect(find.text('上海酒店竞品监控'), findsOneWidget);
    expect(find.text('我的监控项目'), findsOneWidget);
  });

  testWidgets('清除按钮把所有项目的今日查看归零并保存', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    const storage = ProjectStorage();
    await storage.saveProjects([
      MonitorProject(
        name: '项目一',
        city: '上海',
        mainHotel: HotelConfig.fromLegacyName('酒店一'),
        competitors: [],
        todayViewCount: 4,
      ),
      MonitorProject(
        name: '项目二',
        city: '杭州',
        mainHotel: HotelConfig.fromLegacyName('酒店二'),
        competitors: [],
        todayViewCount: 7,
      ),
    ]);

    await tester.pumpWidget(const HotelMonitorApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('清除'));
    await tester.pumpAndSettle();

    expect(find.text('今日查看：0 次'), findsWidgets);
    final savedProjects = await storage.loadProjects();
    expect(
      savedProjects!.map((project) => project.todayViewCount),
      everyElement(0),
    );
  });

  testWidgets('总按钮汇总显示所有项目的当前采集结果', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    const storage = ProjectStorage();
    final project = MonitorProject(
      name: '汇总测试项目',
      city: '成都',
      mainHotel: HotelConfig.fromLegacyName('测试酒店'),
      competitors: [],
    );
    project.ctripReports[project.mainHotel.id] = HotelReportHistory(
      current: ReportData(
        time: DateTime(2026, 7, 18, 12),
        platform: '携程',
        hotelName: '测试酒店',
        roomNames: const [],
        resultStatus: 'full',
      ),
    );
    await storage.saveProjects([project]);

    await tester.pumpWidget(const HotelMonitorApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('总 · 查看全部结果'));
    await tester.pumpAndSettle();

    expect(find.text('全部项目采集结果'), findsOneWidget);
    expect(find.text('1 个项目 · 1 条结果'), findsOneWidget);
    expect(find.text('状态：满房'), findsOneWidget);
  });

  testWidgets('全按钮使用三秒倒计时确认弹窗避免误触', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const HotelMonitorApp());
    await tester.pumpAndSettle();

    await tester.tap(find.text('全 · 采集所有项目'));
    await tester.pump();
    expect(find.text('确认采集全部项目？'), findsOneWidget);
    expect(find.text('3 秒后自动确认并开始'), findsOneWidget);

    await tester.pump(const Duration(seconds: 1));
    expect(find.text('2 秒后自动确认并开始'), findsOneWidget);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(find.text('确认采集全部项目？'), findsNothing);
  });

  testWidgets('更新弹窗阻挡状态会显示明确的人工处理提醒', (WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ReportCard(
            report: ReportData(
              time: DateTime(2026, 7, 18, 12),
              platform: '美团',
              hotelName: '测试酒店',
              roomNames: const [],
              resultStatus: 'update_blocked',
            ),
            isPrevious: false,
          ),
        ),
      ),
    );

    expect(find.text('状态：更新弹窗阻挡'), findsOneWidget);
    expect(find.textContaining('本次采集已停止'), findsOneWidget);
  });

  testWidgets('验证码提示卡显示剩余任务并提供继续入口', (WidgetTester tester) async {
    var resumed = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CaptchaPendingCard(
            state: const {
              'platform': '美团',
              'hotelName': '测试酒店',
              'pendingCount': 3,
            },
            resuming: false,
            onResume: () => resumed = true,
            onDiscard: () {},
          ),
        ),
      ),
    );

    expect(find.text('需要人工完成验证码'), findsOneWidget);
    expect(find.textContaining('保留 3 个未完成任务'), findsOneWidget);
    await tester.tap(find.text('继续未完成任务'));
    expect(resumed, isTrue);
  });

  testWidgets('项目窗口可按模板批量添加主体和竞对酒店', (WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () => showProjectDialog(context),
              child: const Text('打开项目窗口'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('打开项目窗口'));
    await tester.pumpAndSettle();
    expect(find.text('批量添加'), findsOneWidget);

    await tester.tap(find.byKey(const Key('batchAddHotelsButton')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('batchFullScreenEditor')), findsOneWidget);
    final input = tester.widget<TextField>(
      find.byKey(const Key('batchHotelInput')),
    );
    expect(input.decoration?.hintText, contains('主体酒店'));
    expect(input.decoration?.hintText, contains('平台无名称填 1'));

    await tester.enterText(find.byKey(const Key('batchHotelInput')), '''
主体酒店
主体美团
主体携程

A
竞对A美团
1

B
竞对B美团
竞对B携程
''');
    await tester.tap(find.byKey(const Key('confirmBatchAddButton')));
    await tester.pumpAndSettle();

    expect(find.text('竞对A美团'), findsOneWidget);
    expect(find.text('竞对B美团 / 竞对B携程'), findsOneWidget);
    expect(find.text('主体美团'), findsOneWidget);
    expect(find.text('主体携程'), findsOneWidget);
  });
}
