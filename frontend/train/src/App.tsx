import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { MainLayout } from '@/layouts/MainLayout';
import { HomePage } from '@/pages/HomePage';
import { SearchResultsPage } from '@/pages/SearchResultsPage';
import { TrainDetailsPage } from '@/pages/TrainDetailsPage';
import { BookingPage } from '@/pages/BookingPage';
import { MyTripsPage } from '@/pages/MyTripsPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { HotelPage } from '@/pages/HotelPage';
import { CabPage } from '@/pages/CabPage';
import { NotificationsPage } from '@/pages/NotificationsPage';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ProfilePage } from '@/pages/ProfilePage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchResultsPage />} />
          <Route path="/trains/:trainId" element={<TrainDetailsPage />} />
          <Route path="/book/:trainId" element={<BookingPage />} />
          <Route path="/trips" element={<MyTripsPage />} />
          <Route path="/trips/:id" element={<DashboardPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/hotel/:id" element={<HotelPage />} />
          <Route path="/cab/:id" element={<CabPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<HomePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
