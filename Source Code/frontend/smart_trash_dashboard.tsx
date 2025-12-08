import React, { useState, useEffect } from 'react';
import { Camera, Trash2, AlertTriangle, Calendar, RefreshCw, Wifi, WifiOff } from 'lucide-react';
import Login from './Login';

const SmartTrashDashboard = () => {
  const [activeTab, setActiveTab] = useState('overview');
  const [isConnected, setIsConnected] = useState(true);
  const [liveImageUrl, setLiveImageUrl] = useState<string | undefined>(undefined);
  const [trashType, setTrashType] = useState<'organic' | 'inorganic' | 'unknown'>('unknown');
  const [binType, setBinType] = useState<string | null>(null); // ORGANIC hoặc INORGANIC khi cảnh báo
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
    { time: string; type: 'Hữu cơ' | 'Vô cơ' | 'Chưa rõ'; source: string }[]
  >([]);

  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authHeader, setAuthHeader] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<string | null>(null);

  const apiBaseUrl = 'http://localhost:8080/api';
  const socketUrl = 'wss://ntdung.systems/ws';

  const handleLogout = () => {
    setIsAuthenticated(false);
    setAuthHeader(null);
    setCurrentUser(null);
    localStorage.removeItem('authHeader');
    localStorage.removeItem('username');
  };

  // Restore session on reload
  useEffect(() => {
    const savedAuth = localStorage.getItem('authHeader');
    const savedUser = localStorage.getItem('username');
    if (savedAuth && savedUser) {
      setAuthHeader(savedAuth);
      setCurrentUser(savedUser);
      setIsAuthenticated(true);
    }
  }, []);

  const appendEvent = (typeLabel: 'Hữu cơ' | 'Vô cơ' | 'Chưa rõ', source: string, receivedAt?: number) => {
    setRecentLogs(prev => {
      const formatter = new Intl.DateTimeFormat('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
      const timeStr = receivedAt ? formatter.format(new Date(receivedAt)) : formatter.format(new Date());
      const updated = [{ time: timeStr, type: typeLabel, source }, ...prev];
      return updated.slice(0, 50);
    });
  };

  useEffect(() => {
    if (!isAuthenticated || !authHeader) return;
    let ws: WebSocket | null = null;
    let liveInterval: ReturnType<typeof setInterval>;

    const connectWebSocket = () => {
      ws = new WebSocket(socketUrl);
      ws.onopen = () => setIsConnected(true);
      ws.onclose = () => setIsConnected(false);
      ws.onerror = () => setIsConnected(false);
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);

          const dataVal = msg?.payload?.data || msg?.data || msg?.latestEsp32Data?.data;
          if (dataVal === 'ROTATE_CW') {
            setTrashType('inorganic');
            setLastClassification(prev => ({ ...prev, type: 'inorganic' }));
            appendEvent('Vô cơ', 'WebSocket', msg?.payload?.receivedAt || msg?.latestEsp32Data?.receivedAt);
          } else if (dataVal === 'ROTATE_CCW') {
            setTrashType('organic');
            setLastClassification(prev => ({ ...prev, type: 'organic' }));
            appendEvent('Hữu cơ', 'WebSocket', msg?.payload?.receivedAt || msg?.latestEsp32Data?.receivedAt);
          }

          // Xử lý cảnh báo từ binType trong WebSocket message
          const binTypeVal = msg?.latestEsp32Data?.binType || msg?.payload?.binType;
          if (binTypeVal && (binTypeVal === 'ORGANIC' || binTypeVal === 'INORGANIC')) {
            setBinType(binTypeVal);
          } else if (!binTypeVal || binTypeVal === '') {
            setBinType(null);
          }

          if (msg?.latestEsp32Image?.data) {
            const url = `data:${msg.latestEsp32Image.contentType || 'image/jpeg'};base64,${msg.latestEsp32Image.data}`;
            setLiveImageUrl(url);
            setLastClassification(prev => ({
              ...prev,
              imageUrl: url,
              time: msg.latestEsp32Image.receivedAt
                ? new Date(msg.latestEsp32Image.receivedAt).toLocaleTimeString('vi-VN')
                : prev.time,
              type: prev.type
            }));
          }
          if (msg?.latestEsp32Data?.data) {
            setIsConnected(true);
          }
        } catch (e) {
          console.error('Invalid WS payload', e);
        }
      };
    };

    connectWebSocket();

    const loadLive = async () => {
      try {
        const res = await fetch(`${apiBaseUrl}/live`, {
          headers: { Authorization: authHeader }
        });
        if (!res.ok) throw new Error('Không thể tải dữ liệu live từ backend');
        const live = await res.json();

        if (live?.status === 'ok' && (live?.activeConnections ?? 0) > 0) {
          setIsConnected(true);
        }

        if (live?.trashType) {
          if (live.trashType === 'organic' || live.trashType === 'inorganic') {
            setTrashType(live.trashType);
            setLastClassification(prev => ({ ...prev, type: live.trashType === 'organic' ? 'organic' : 'inorganic' }));
          }
        }

        // Xử lý cảnh báo từ binType
        if (live?.binType && (live.binType === 'ORGANIC' || live.binType === 'INORGANIC')) {
          setBinType(live.binType);
        } else if (live?.latestEsp32Data?.binType && (live.latestEsp32Data.binType === 'ORGANIC' || live.latestEsp32Data.binType === 'INORGANIC')) {
          setBinType(live.latestEsp32Data.binType);
        } else {
          // Nếu không có binType hoặc binType rỗng, xóa cảnh báo
          setBinType(null);
        }

        // Chỉ cập nhật ảnh khi có ảnh mới thực sự, không reset về undefined
        if (live?.latestEsp32Image?.data) {
          const url = `data:${live.latestEsp32Image.contentType || 'image/jpeg'};base64,${live.latestEsp32Image.data}`;
          setLiveImageUrl(url);
          setLastClassification(prev => ({
            ...prev,
            imageUrl: url,
            time: live.latestEsp32Image.receivedAt
              ? new Date(live.latestEsp32Image.receivedAt).toLocaleTimeString('vi-VN')
              : prev.time
          }));
        }
        // Nếu không có ảnh mới, giữ nguyên ảnh hiện tại (không làm gì cả)
      } catch {
        setIsConnected(false);
      }
    };

    loadLive();
    liveInterval = setInterval(loadLive, 5000);

    const loadOverviewAndLogs = async () => {
      try {
        setIsLoading(true);
        setError(null);

        const [overviewRes, logsRes] = await Promise.all([
          fetch(`${apiBaseUrl}/overview`, {
            headers: { Authorization: authHeader }
          }),
          fetch(`${apiBaseUrl}/events`, {
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

        // Chỉ cập nhật ảnh nếu có ảnh mới, giữ nguyên nếu không có
        setLastClassification(prev => ({
          type: overview.lastClassification?.type === 'inorganic' ? 'inorganic' :
            overview.lastClassification?.type === 'organic' ? 'organic' : prev.type,
          confidence: overview.lastClassification?.confidence ?? prev.confidence,
          time: overview.lastClassification?.time ?? prev.time,
          imageUrl: overview.lastClassification?.imageUrl ?? prev.imageUrl  // Giữ nguyên nếu không có ảnh mới
        }));

        setStats(prev => [
          {
            ...prev[0],
            value: overview.totalClassifications?.toLocaleString('vi-VN') ?? '0',
            change: ''
          },
          {
            ...prev[1],
            value: `${overview.todayCount ?? 0}`,
            change: ''
          },
          {
            ...prev[2],
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
          (logs as any[]).map((log: any) => ({
            time: formatter.format(new Date(log.receivedAt)),
            type: log.trashType === 'inorganic' ? 'Vô cơ' : log.trashType === 'organic' ? 'Hữu cơ' : 'Chưa rõ',
            source: log.filename || (log.eventType === 'IMAGE' ? 'Ảnh mới' : 'WebSocket')
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
    return () => {
      clearInterval(interval);
      if (liveInterval) clearInterval(liveInterval);
      if (ws) ws.close();
    };
  }, [isAuthenticated, authHeader]);

  if (!isAuthenticated || !authHeader) {
    return (
      <Login
        apiBaseUrl={apiBaseUrl}
        onLoginSuccess={({ authHeader, username }) => {
          setAuthHeader(authHeader);
          setCurrentUser(username);
          setIsAuthenticated(true);
          setError(null);
          localStorage.setItem('authHeader', authHeader);
          localStorage.setItem('username', username);
        }}
      />
    );
  }

  const renderOverview = () => {
    const alertStat = stats.find(s => s.title === 'Cảnh báo');
    const otherStats = stats.filter(s => s.title !== 'Cảnh báo');

    // Xác định cảnh báo từ binType
    const alertMessage = binType === 'ORGANIC'
      ? 'Rác hữu cơ đầy!'
      : binType === 'INORGANIC'
        ? 'Rác vô cơ đầy!'
        : alertStat?.change || 'Ổn định';
    const hasAlert = binType === 'ORGANIC' || binType === 'INORGANIC';

    return (
      <div className="space-y-6">
        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {otherStats.map((stat, index) => (
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

        {/* Cảnh báo và Ảnh song song */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Cảnh báo Card - luôn hiển thị */}
          <div className={`bg-white rounded-lg shadow-md p-6 border-l-4 ${hasAlert
            ? (binType === 'ORGANIC' ? 'border-green-500' : 'border-blue-500')
            : 'border-gray-300'
            }`}>
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center mb-2">
                  <AlertTriangle className={`w-5 h-5 mr-2 ${hasAlert
                    ? (binType === 'ORGANIC' ? 'text-green-600' : 'text-blue-600')
                    : 'text-gray-400'
                    }`} />
                  <p className="text-gray-500 text-sm font-semibold">Tình trạng</p>
                </div>
                <h3 className={`text-xl font-bold ${hasAlert
                  ? (binType === 'ORGANIC' ? 'text-green-600' : 'text-blue-600')
                  : 'text-gray-600'
                  }`}>
                  {hasAlert ? alertMessage : 'Ổn định'}
                </h3>
                <p className="text-sm text-gray-600 mt-2">
                  {hasAlert
                    ? (binType === 'ORGANIC'
                      ? 'Thùng rác hữu cơ đã đầy, vui lòng đổ rác ngay!'
                      : 'Thùng rác vô cơ đã đầy, vui lòng đổ rác ngay!')
                    : 'Tất cả thùng rác đang hoạt động bình thường'}
                </p>
              </div>
              <div className={`${hasAlert
                ? (binType === 'ORGANIC' ? 'bg-green-500' : 'bg-blue-500')
                : 'bg-gray-400'
                } text-white p-3 rounded-lg`}>
                <AlertTriangle className="w-6 h-6" />
              </div>
            </div>
          </div>

          {/* Latest Classification Image */}
          <div className="bg-white rounded-lg shadow-md p-6">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">Ảnh rác vừa phân loại</h3>
              <Camera className="w-5 h-5 text-gray-400" />
            </div>
            <div className="bg-gray-900 rounded-lg aspect-[4/3] max-w-md mx-auto flex items-center justify-center relative overflow-hidden">
              {lastClassification.imageUrl ? (
                <img
                  src={lastClassification.imageUrl || liveImageUrl}
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
                <span className="text-gray-600">Thời gian:</span>
                <span className="font-semibold">{lastClassification.time}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  };

  const renderLogs = () => (
    <div className="bg-white rounded-lg shadow-md p-6">
      <div className="mb-4">
        <h3 className="text-lg font-semibold">Nhật ký phân loại</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b">
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Thời gian</th>
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-600">Loại rác</th>
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
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'overview' && renderOverview()}
        {activeTab === 'logs' && renderLogs()}
      </main>
    </div>
  );
};

export default SmartTrashDashboard;