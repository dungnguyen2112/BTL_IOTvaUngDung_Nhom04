import React, { useState } from 'react';
import { Trash2 } from 'lucide-react';

type LoginProps = {
  apiBaseUrl: string;
  onLoginSuccess: (data: { authHeader: string; username: string }) => void;
};

const Login: React.FC<LoginProps> = ({ apiBaseUrl, onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const authHeader = 'Basic ' + btoa(`${username}:${password}`);

      // Real login check: call a protected endpoint
      const res = await fetch(`${apiBaseUrl}/overview`, {
        headers: {
          Authorization: authHeader
        }
      });

      if (!res.ok) {
        throw new Error('Sai tài khoản hoặc mật khẩu');
      }

      onLoginSuccess({ authHeader, username });
    } catch (err: any) {
      setError(err.message ?? 'Đăng nhập thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-md">
        <div className="flex items-center mb-6">
          <Trash2 className="w-8 h-8 text-green-600 mr-3" />
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Smart Trash System</h1>
            <p className="text-sm text-gray-500">Đăng nhập để truy cập dashboard</p>
          </div>
        </div>
        {error && (
          <p className="text-sm text-red-600 mb-4">
            {error}
          </p>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Tên đăng nhập
            </label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="admin hoặc user"
              autoComplete="username"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Mật khẩu
            </label>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              placeholder="admin123 hoặc user123"
              autoComplete="current-password"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;


