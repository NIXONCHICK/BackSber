import { type NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    // URL вашего реального бэкенд API для инициации парсинга
    const backendUrl = `${process.env.BACKEND_API_URL}/api/user/initiate-parsing`;

    // Получаем Authorization header из входящего запроса от клиента
    const authToken = request.headers.get('Authorization');

    const headers: HeadersInit = {
      // 'Content-Type': 'application/json', // Тело запроса не отправляется, так что Content-Type не так важен
    };
    if (authToken) {
      headers['Authorization'] = authToken;
    } else {
      // Если токена нет, это ошибка, так как эндпоинт требует аутентификации
      return NextResponse.json({ message: 'Отсутствует токен авторизации для инициации парсинга' }, { status: 401 });
    }

    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: headers,
      // Тело запроса (body) не нужно, так как бэкенд использует @AuthenticationPrincipal
    });

    // Пытаемся прочитать тело ответа, даже если оно может быть пустым при статусе 204 No Content
    let data;
    try {
      data = await backendResponse.json();
    } catch (e) {
      // Если .json() падает (например, пустой ответ), data останется undefined
      if (backendResponse.ok && backendResponse.status === 204) {
        data = { message: "Запрос на парсинг принят." }; // Или другое подходящее сообщение
      } else if (backendResponse.ok) {
        data = { message: "Ответ от сервера не в формате JSON, но запрос успешен." };
      } else {
         // Если и так ошибка, data останется undefined, и мы вернем стандартную ошибку ниже
      }
    }


    if (!backendResponse.ok) {
      // Если data существует и содержит message, используем его, иначе стандартное сообщение
      const errorMessage = data?.message || 'Ошибка на стороне бэкенда при инициации парсинга.';
      return NextResponse.json({ message: errorMessage }, { status: backendResponse.status });
    }

    // Возвращаем ответ от бэкенда клиенту
    // Если data undefined (например, при ошибке json() и не 204), можно вернуть стандартный успешный ответ
    return NextResponse.json(data || { message: 'Парсинг успешно инициирован (или уже был выполнен).' }, { status: backendResponse.status });

  } catch (error) {
    console.error('[API INITIATE PARSING PROXY ERROR]', error);
    let message = 'Неизвестная ошибка сервера при попытке инициации парсинга';
    if (error instanceof Error) {
        message = error.message || message;
    }
    return NextResponse.json({ message }, { status: 500 });
  }
} 