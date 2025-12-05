import React, { useState, useEffect } from 'react';
import { Camera, Trash2, AlertTriangle, TrendingUp, Calendar, Download, RefreshCw, Wifi, WifiOff } from 'lucide-react';
import Login from './Login';
import Register from './Register';

const SmartTrashDashboard = () => {
  const [activeTab, setActiveTab] = useState('overview');
  const [isConnected, setIsConnected] = useState(true);
  const [organicLevel, setOrganicLevel] = useState(0);
  const [inorganicLevel, setInorganicLevel] = useState(0);
  const [lastClassification, setLastClassification] = useState<{
    type: 'organic' | 'inorganic';
    confidence: number;
    time: string;
    imageUrl?: string;
  }>({
    type: 'organic',
    confidence: 0,
    time: '-',
    imageUrl: undefined
  });

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [stats, setStats] = useState([
    {
      title: 'Tổng số lần phân loại',
      value: '0',
      change: '',
      icon: <Trash2 className="w-6 h-6" />,
      color: 'bg-blue-500'
    },
    {
      title: 'Độ chính xác TB',
      value: '0%',
      change: '',
      icon: <TrendingUp className="w-6 h-6" />,
      color: 'bg-green-500'
    },
    {
      title: 'Hôm nay',
      value: '0',
      change: '',
      icon: <Calendar className="w-6 h-6" />,
      color: 'bg-purple-500'
    },
    {
      title: 'Cảnh báo',
      value: '0',
      change: '',
      icon: <AlertTriangle className="w-6 h-6" />,
      color: 'bg-orange-500'
    }
  ]);

  const [recentLogs, setRecentLogs] = useState<
    { time: string; type: 'Hữu cơ' | 'Vô cơ'; confidence: number; status: string }[]
  >([]);

  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authHeader, setAuthHeader] = useState<string | null>(null);
  const [showRegister, setShowRegister] = useState(false);
  const [currentUser, setCurrentUser] = useState<string | null>(null);

  const apiBaseUrl = 'http://localhost:8080/api';

  const handleLogout = () => {
    setIsAuthenticated(false);
    setAuthHeader(null);
    setCurrentUser(null);
  };

  useEffect(() => {
    if (!isAuthenticated || !authHeader) return;
    const loadOverviewAndLogs = async () => {
      try {
        setIsLoading(true);
        setError(null);

        const [overviewRes, logsRes] = await Promise.all([
          fetch(`${apiBaseUrl}/overview`, {
            headers: { Authorization: authHeader }
          }),
          fetch(`${apiBaseUrl}/logs`, {
            headers: { Authorization: authHeader }
          })
        ]);

        if (!overviewRes.ok || !logsRes.ok) {
          throw new Error('Không thể tải dữ liệu từ backend');
        }

        const overview = await overviewRes.json();
        const logs = await logsRes.json();

        setOrganicLevel(overview.organicLevel ?? 0);
        setInorganicLevel(overview.inorganicLevel ?? 0);
        setLastClassification({
          type: overview.lastClassification?.type === 'inorganic' ? 'inorganic' : 'organic',
          confidence: overview.lastClassification?.confidence ?? 0,
          time: overview.lastClassification?.time ?? '-',
          imageUrl: overview.lastClassification?.imageUrl
        });

        setStats(prev => [
          {
            ...prev[0],
            value: overview.totalClassifications?.toLocaleString('vi-VN') ?? '0',
            change: '+12%'
          },
          {
            ...prev[1],
            value: `${overview.averageAccuracy ?? 0}%`,
            change: '+2.1%'
          },
          {
            ...prev[2],
            value: `${overview.todayCount ?? 0}`,
            change: '+8'
          },
          {
            ...prev[3],
            value: `${overview.alertCount ?? 0}`,
            change: overview.alertMessage ?? ''
          }
        ]);

        const formatter = new Intl.DateTimeFormat('vi-VN', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        });

        setRecentLogs(
          (logs as any[]).map(log => ({
            time: formatter.format(new Date(log.timestamp)),
            type: log.type === 'Vô cơ' ? 'Vô cơ' : 'Hữu cơ',
            confidence: log.confidence,
            status: log.status
          }))
        );

        setIsConnected(true);
      } catch (e: any) {
        setError(e.message ?? 'Lỗi kết nối backend');
        setIsConnected(false);
      } finally {
        setIsLoading(false);
      }
    };

    loadOverviewAndLogs();
    const interval = setInterval(loadOverviewAndLogs, 10000);
    return () => clearInterval(interval);
  }, [isAuthenticated, authHeader]);

  if (!isAuthenticated || !authHeader) {
    if (showRegister) {
      return (
        <Register
          apiBaseUrl={apiBaseUrl}
          onRegisterSuccess={() => {
            setShowRegister(false);
            setError(null);
          }}
          onBackToLogin={() => setShowRegister(false)}
        />
      );
    }

    return (
      <Login
        apiBaseUrl={apiBaseUrl}
        onLoginSuccess={({ authHeader, username }) => {
          setAuthHeader(authHeader);
          setCurrentUser(username);
          setIsAuthenticated(true);
          setError(null);
        }}
        onRegisterClick={() => setShowRegister(true)}
      />
    );
  }

  const renderOverview = () => (
    <div className="space-y-6">
      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat, index) => (
          <div key={index} className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-500 text-sm">{stat.title}</p>
                <h3 className="text-2xl font-bold mt-1">{stat.value}</h3>
                <p className="text-sm text-green-600 mt-1">{stat.change}</p>
              </div>
              <div className={`${stat.color} text-white p-3 rounded-lg`}>
                {stat.icon}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Bin Level Status */}
        <div className="bg-white rounded-lg shadow-md p-6">
          <h3 className="text-lg font-semibold mb-4">Mức đầy thùng rác</h3>
          {error && (
            <p className="text-xs text-red-600 mb-3">
              {error}
            </p>
          )}

          {/* Organic Bin */}
          <div className="mb-6">
            <div className="flex justify-between items-center mb-2">
              <span className="text-sm font-medium text-gray-700">Rác hữu cơ</span>
              <span className="text-sm font-bold text-green-600">{organicLevel.toFixed(1)}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-4 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${organicLevel > 90 ? 'bg-red-500' : organicLevel > 75 ? 'bg-orange-500' : 'bg-green-500'
                  }`}
                style={{ width: `${organicLevel}%` }}
              />
            </div>
            {organicLevel > 75 && (
              <p className="text-xs text-orange-600 mt-1 flex items-center">
                <AlertTriangle className="w-3 h-3 mr-1" />
                {organicLevel > 90 ? 'Cần đổ rác ngay!' : 'Sắp đầy'}
              </p>
            )}
          </div>

          {/* Inorganic Bin */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <span className="text-sm font-medium text-gray-700">Rác vô cơ</span>
              <span className="text-sm font-bold text-blue-600">{inorganicLevel.toFixed(1)}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-4 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${inorganicLevel > 90 ? 'bg-red-500' : inorganicLevel > 75 ? 'bg-orange-500' : 'bg-blue-500'
                  }`}
                style={{ width: `${inorganicLevel}%` }}
              />
            </div>
          </div>
        </div>

        {/* Latest Classification Image */}
        <div className="bg-white rounded-lg shadow-md p-6">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">Ảnh rác vừa phân loại</h3>
            <Camera className="w-5 h-5 text-gray-400" />
          </div>
          <div className="bg-gray-900 rounded-lg aspect-video flex items-center justify-center relative overflow-hidden">
            {lastClassification.imageUrl ? (
              <img 
                src={lastClassification.imageUrl} 
                alt="Rác vừa phân loại" 
                className="w-full h-full object-cover"
              />
            ) : (
              <>
                <div className="absolute inset-0 bg-gradient-to-br from-gray-800 to-gray-900"></div>
                <div className="relative z-10 text-center">
                  <Camera className="w-16 h-16 text-gray-600 mx-auto mb-2" />
                  <p className="text-gray-400 text-sm">Chưa có ảnh phân loại...</p>
                </div>
              </>
            )}
            {lastClassification.imageUrl && (
              <div className={`absolute top-2 right-2 ${lastClassification.type === 'organic' ? 'bg-green-500' : 'bg-blue-500'} text-white px-3 py-1 rounded-full text-xs font-semibold`}>
                {lastClassification.type === 'organic' ? 'Hữu cơ' : 'Vô cơ'}
              </div>
            )}
          </div>
          <div className="mt-4 bg-gray-50 rounded p-3">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Phân loại gần nhất:</span>
              <span className={`font-semibold ${lastClassification.type === 'organic' ? 'text-green-600' : 'text-blue-600'}`}>
                {lastClassification.type === 'organic' ? 'Hữu cơ' : 'Vô cơ'}
              </span>
            </div>
            <div className="flex justify-between text-sm mt-1">
              <span className="text-gray-600">Độ tin cậy:</span>
              <span className="font-semibold">{lastClassification.confidence}%</span>
            </div>
            <div className="flex justify-between text-sm mt-1">
              <span className="text-gray-600">Thời gian:</span>
              <span className="font-semibold">{lastClassification.time}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  const renderLogs = () => (
    <div className="bg-white rounded-lg shadow-md p-6">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold">Nhật ký phân loại</h3>
        <button className="flex items-center text-sm text-blue-600 hover:text-blue-700">
          <Download className="w-4 h-4 mr-1" />
          Xuất CSV
        </button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b">
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Thời gian</th>
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Loại rác</th>
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Độ tin cậy</th>
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {recentLogs.map((log, index) => (
              <tr key={index} className="border-b hover:bg-gray-50">
                <td className="py-3 px-4 text-sm">{log.time}</td>
                <td className="py-3 px-4">
                  <span className={`inline-block px-3 py-1 rounded-full text-xs font-semibold ${log.type === 'Hữu cơ' ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'
                    }`}>
                    {log.type}
                  </span>
                </td>
                <td className="py-3 px-4 text-sm font-medium">{log.confidence}%</td>
                <td className="py-3 px-4">
                  <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-700">
                    Thành công
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );

  const renderSettings = () => (
    <div className="space-y-6">
      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold mb-4">Cấu hình hệ thống</h3>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Ngưỡng cảnh báo đầy (%)
            </label>
            <input
              type="number"
              className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              defaultValue="75"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Ngưỡng độ tin cậy tối thiểu (%)
            </label>
            <input
              type="number"
              className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              defaultValue="85"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              WebSocket Server URL
            </label>
            <input
              type="text"
              className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              defaultValue="ws://localhost:8765"
            />
          </div>

          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-gray-700">Tự động đổ rác khi đầy</span>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer" />
              <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>

          <button className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 transition-colors">
            Lưu cấu hình
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-md p-6">
        <h3 className="text-lg font-semibold mb-4">Thông tin thiết bị</h3>
        <div className="space-y-3 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-600">Model:</span>
            <span className="font-semibold">ESP32-CAM</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">Firmware:</span>
            <span className="font-semibold">v1.2.3</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">IP Address:</span>
            <span className="font-semibold">192.168.1.100</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">Uptime:</span>
            <span className="font-semibold">3d 12h 45m</span>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <div className="flex justify-between items-center">
            <div className="flex items-center">
              <Trash2 className="w-8 h-8 text-green-600 mr-3" />
              <div>
                <h1 className="text-2xl font-bold text-gray-900">Smart Trash System</h1>
                <p className="text-sm text-gray-500">Hệ thống phân loại rác thông minh</p>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex flex-col items-end mr-4 text-right">
                <span className="text-xs text-gray-500">Đang đăng nhập</span>
                <span className="text-sm font-semibold text-gray-800">{currentUser}</span>
              </div>
              <div className={`flex items-center ${isConnected ? 'text-green-600' : 'text-red-600'}`}>
                {isConnected ? <Wifi className="w-5 h-5 mr-1" /> : <WifiOff className="w-5 h-5 mr-1" />}
                <span className="text-sm font-medium">{isConnected ? 'Đã kết nối' : 'Mất kết nối'}</span>
              </div>
              <button className="p-2 hover:bg-gray-100 rounded-lg transition-colors">
                <RefreshCw className="w-5 h-5 text-gray-600" />
              </button>
              <button
                onClick={handleLogout}
                className="ml-2 px-3 py-1 text-sm font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
              >
                Đăng xuất
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation */}
      <nav className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex space-x-8">
            <button
              onClick={() => setActiveTab('overview')}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${activeTab === 'overview'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
            >
              Tổng quan
            </button>
            <button
              onClick={() => setActiveTab('logs')}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${activeTab === 'logs'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
            >
              Nhật ký
            </button>
            <button
              onClick={() => setActiveTab('settings')}
              className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${activeTab === 'settings'
                ? 'border-blue-600 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
            >
              Cài đặt
            </button>
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'overview' && renderOverview()}
        {activeTab === 'logs' && renderLogs()}
        {activeTab === 'settings' && renderSettings()}
      </main>
    </div>
  );
};

export default SmartTrashDashboard;