import 'package:flutter_test/flutter_test.dart';
import 'package:hotel_monitor_app/models/monitor_project.dart';
import 'package:hotel_monitor_app/services/project_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  const storage = ProjectStorage();

  test('首次使用时返回 null', () async {
    SharedPreferences.setMockInitialValues({});
    expect(await storage.loadProjects(), isNull);
  });

  test('主动保存的空列表在重启后仍为空', () async {
    SharedPreferences.setMockInitialValues({});
    await storage.saveProjects(const <MonitorProject>[]);
    expect(await storage.loadProjects(), isEmpty);
  });

  test('损坏的数据会报告错误且不会被覆盖', () async {
    SharedPreferences.setMockInitialValues({
      ProjectStorage.projectsKey: '{broken json',
    });
    expect(storage.loadProjects(), throwsFormatException);
  });
}
