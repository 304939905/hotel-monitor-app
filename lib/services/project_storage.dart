import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/monitor_project.dart';

class ProjectStorage {
  static const String projectsKey = 'hotel_monitor_projects';

  const ProjectStorage();

  /// Returns null only when the app has never saved project data.
  Future<List<MonitorProject>?> loadProjects() async {
    final preferences = await SharedPreferences.getInstance();
    final savedData = preferences.getString(projectsKey);
    if (savedData == null) return null;

    final decoded = jsonDecode(savedData);
    if (decoded is! List) {
      throw const FormatException('监控项目数据格式不正确');
    }

    return decoded
        .whereType<Map>()
        .map((item) => MonitorProject.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  Future<void> saveProjects(List<MonitorProject> projects) async {
    final preferences = await SharedPreferences.getInstance();
    final encoded = jsonEncode(
      projects.map((project) => project.toJson()).toList(),
    );
    final saved = await preferences.setString(projectsKey, encoded);
    if (!saved) {
      throw StateError('监控项目保存失败');
    }
  }
}
