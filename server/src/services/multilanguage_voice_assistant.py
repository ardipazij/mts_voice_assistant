import json
import re
import os
import sys
import io
import platform
import locale
import requests
from typing import Dict, Any, Optional, List, Tuple, Literal
import time
from langdetect import detect

# Словарь для преобразования числительных
NUMBER_WORDS = {
    "один": 1,
    "два": 2,
    "три": 3,
    "четыре": 4,
    "пять": 5,
    "шесть": 6,
    "семь": 7,
    "восемь": 8,
    "девять": 9,
    "десять": 10,
    "одиннадцать": 11,
    "двенадцать": 12,
}

# Словарь для преобразования периодов суток
TIME_PERIODS = {"утра": "00", "дня": "12", "вечера": "12", "ночи": "00"}

# Загрузка локализаций
def load_locales():
    locales = {}
    for lang in ['ru', 'en', 'es']:
        with open(f'services/locales/{lang}.json', 'r', encoding='utf-8') as f:
            locales[lang] = json.load(f)
    return locales

LOCALES = load_locales()

def detect_language(text: str) -> str:
    """Определяет язык текста"""
    try:
        # Проверяем наличие кириллицы
        if any('\u0400' <= c <= '\u04FF' for c in text):
            return 'ru'
        
        # Проверяем наличие испанских символов
        if any(c in 'áéíóúñü' for c in text.lower()):
            return 'es'
            
        # Проверяем наличие английских слов и конструкций
        english_indicators = [
            r'\b(?:call|to|the|mom|dad|brother|sister|friend|set|alarm|create|note|search|open|browser)\b',
            r'\b(?:what|when|where|why|how|who|which|whose)\b',
            r'\b(?:am|is|are|was|were|be|being|been)\b',
            r'\b(?:have|has|had|do|does|did|will|would|shall|should)\b',
            r'\b(?:can|could|may|might|must|ought|need|dare)\b'
        ]
        
        text_lower = text.lower()
        for pattern in english_indicators:
            if re.search(pattern, text_lower):
                return 'en'
        
        # Если нет специальных символов и не найдены английские индикаторы,
        # используем langdetect
        lang = detect(text)
        # Маппинг кодов языков на наши локали
        lang_map = {
            'ru': 'ru',
            'en': 'en',
            'es': 'es'
        }
        return lang_map.get(lang, 'en')  # По умолчанию английский
    except Exception as e:
        print(f"Ошибка при определении языка: {e}")
        return 'en'  # В случае ошибки возвращаем английский

def safe_input(prompt="", encoding="utf-8"):
    """Безопасный ввод с обработкой кодировки"""
    try:
        return input(prompt)
    except UnicodeDecodeError:
        # Если возникла ошибка декодирования, пробуем использовать альтернативный подход
        if platform.system() == "Windows":
            try:
                import msvcrt

                print(prompt, end="", flush=True)
                line = ""
                while True:
                    if msvcrt.kbhit():
                        char = msvcrt.getche().decode(encoding, errors="replace")
                        if char == "\r":
                            print()
                            break
                        line += char
                return line
            except Exception as e:
                print(f"Ошибка при чтении ввода: {e}")
                return ""
        else:
            # Для Unix-подобных систем
            try:
                return input(prompt)
            except:
                print(
                    "Ошибка при чтении ввода. Пожалуйста, введите текст, используя только ASCII символы."
                )
                return input(prompt)


# Настройка кодировки
def setup_encoding():
    if platform.system() == "Windows":
        # Для Windows используем cp1251
        encoding = "cp1251"
        locale_name = "Russian_Russia.1251"
    else:
        # Для Linux и других ОС используем UTF-8
        encoding = "utf-8"
        locale_name = "ru_RU.UTF-8"

    try:
        locale.setlocale(locale.LC_ALL, locale_name)
    except locale.Error:
        print(f"Предупреждение: Не удалось установить локаль {locale_name}")

    return encoding


# Устанавливаем кодировку
encoding = setup_encoding()

COMMANDS: Dict[str, str] = {
    "turn_on_camera": "Включить камеру",
    "turn_off_phone": "Выключить телефон",
    "open_telegram": "Открыть Телеграм",
    "open_whatsapp": "Открыть Ватсап",
    "play_music": "Включить музыку",
    "turn_on_light": "Включить фонарик",
    "turn_off_light": "Выключить фонарик",
    "open_browser": "Открыть браузер",
    "call_contact": "Позвонить контакту",
    "send_message": "Отправить сообщение",
    "activate_voice_search": "Включить голосовой поиск",
    "open_calendar": "Открыть календарь",
    "create_event": "Создать событие",
    "turn_off_sound": "Выключить звук",
    "turn_on_sound": "Включить звук",
    "set_alarm": "Установить будильник",
    "answer_question": "Ответить на вопрос",
    "open_maps": "Открыть карты",
    "open_contacts": "Открыть контакты",
    "open_phone": "Открыть телефон",
    "turn_on_bluetooth": "Включить блютуз",
    "turn_off_bluetooth": "Выключить блютуз",
    "open_settings": "Открыть настройки",
    "open_gallery": "Открыть галерею",
    "open_notes": "Открыть заметки",
    "create_note": "Создать заметку",
    "search_note": "Найти заметку",
    "open_note": "Открыть заметку",
    "open_voice_recorder": "Открыть диктофон",
    "start_recording": "Начать запись",
    "stop_recording": "Закончить запись",
    "open_clock": "Открыть часы",
    "lock_screen": "Блокировка экрана",
}

# шаблоны responses
RESPONSES: Dict[str, str] = {
    "turn_on_camera": "Камера включена.",
    "turn_off_phone": "Выключаю телефон...",
    "open_telegram": "Открываю Телеграм.",
    "open_whatsapp": "Открываю Ватсап.",
    "play_music": "Включаю музыку.",
    "turn_on_light": "Включаю фонарик.",
    "turn_off_light": "Выключаю фонарик.",
    "open_browser": "Открываю браузер.",
    "call_contact": "Звоню контакту.",
    "send_message": "Отправляю сообщение.",
    "activate_voice_search": "Голосовой поиск активирован.",
    "open_calendar": "Открываю календарь.",
    "create_event": "Создаю событие.",
    "turn_off_sound": "Звук выключен.",
    "turn_on_sound": "Звук включен.",
    "set_alarm": "Устанавливаю будильник.",
    "search_internet": "Ищу в интернете.",
    "answer_question": "",
    "open_maps": "Открываю карты.",
    "open_contacts": "Открываю контакты.",
    "unknown": "Извините, я не понимаю запрос.",
    "error": "Извините, произошла ошибка при обработке запроса.",
    "turn_on_bluetooth": "Bluetooth включен.",
    "turn_off_bluetooth": "Bluetooth выключен.",
    "open_settings": "Открываю настройки.",
    "open_gallery": "Открываю галерею.",
    "open_notes": "Открываю заметки.",
    "create_note": "Создаю заметку.",
    "search_note": "Ищу заметки.",
    "open_note": "Открываю заметку.",
    "open_voice_recorder": "Открываю диктофон.",
    "start_recording": "Начинаю запись.",
    "stop_recording": "Запись остановлена.",
    "open_clock": "Открываю часы.",
    "lock_screen": "Экран заблокирован.",
}

# Команды, требующие дополнительных параметров
PARAMETERIZED_COMMANDS: Dict[str, List[str]] = {
    "call_contact": ["contact"],  # Позвонить кому-то
    "send_message": ["contact", "message"],  # Отправить сообщение кому-то
    "set_alarm": ["time"],  # Установить будильник на время
    "create_event": ["title", "time"],  # Создать событие с названием на время
    "search_internet": ["query"],  # Поиск в интернете запроса
    "answer_question": ["query"],  # Ответ на вопрос
    "open_browser": ["query"],  # Поиск в браузере
    "create_note": ["title", "content"],  # Создать заметку
    "search_note": ["query"],  # Поиск по заметкам
    "open_note": ["title"],  # Открыть конкретную заметку
}

# Команды, которые могут предшествовать другим в многошаговом диалоге
COMMAND_TRANSITIONS: Dict[str, Dict[str, List[str]]] = {
    "call_contact": {
        "expected_params": ["contact"],
        "prompts": ["Кому вы хотите позвонить?"],
    },
    "send_message": {
        "expected_params": ["contact", "message"],
        "prompts": ["Кому вы хотите отправить сообщение?", "Что вы хотите отправить?"],
    },
    "set_alarm": {
        "expected_params": ["time"],
        "prompts": ["На какое время установить будильник?"],
    },
    "create_event": {
        "expected_params": ["title", "time"],
        "prompts": ["Какое событие создать?", "На какое время?"],
    },
    "open_browser": {
        "expected_params": ["query"],
        "prompts": ["Что вы хотите найти в интернете?"],
    },
    "create_note": {
        "expected_params": ["title", "content"],
        "prompts": ["Как назвать заметку?", "Что записать в заметку?"],
    },
    "search_note": {
        "expected_params": ["query"],
        "prompts": ["Что искать в заметках?"],
    },
    "open_note": {"expected_params": ["title"], "prompts": ["Какую заметку открыть?"]},
}


class VoiceAssistant:
    """Голосовой помощник для обработки запросов пользователя."""

    def __init__(
        self,
        provider: Literal["ollama", "mistral_api"] = "ollama",
        model_name: str = "mistral",
        ollama_url: str = "http://localhost:11434",
        mistral_api_key: Optional[str] = None,
    ):
        """
        Инициализация голосового помощника.

        Args:
            provider: Провайдер LLM - "ollama" для локальной модели или "mistral_api" для API Mistral
            model_name: Название модели
            ollama_url: URL для доступа к Ollama API (используется только при provider="ollama")
            mistral_api_key: Ключ API Mistral (используется только при provider="mistral_api")
        """
        self.provider = provider
        self.model_name = model_name
        self.ollama_url = ollama_url
        self.mistral_api_key = mistral_api_key or os.environ.get("MISTRAL_API_KEY")
        self.current_language = 'en'  # Язык по умолчанию

        if self.provider == "ollama":
            # Устанавливаем модель по умолчанию для Ollama
            if self.model_name == "mistral" or self.model_name.startswith("mistral-"):
                self.model_name = "mistral"  # Для Ollama используем просто "mistral"
            self._check_ollama_availability()
        elif self.provider == "mistral_api":
            if not self.mistral_api_key:
                raise ValueError(
                    "Ключ API Mistral не предоставлен. Передайте его в конструктор или "
                    "установите переменную окружения MISTRAL_API_KEY."
                )

            # Устанавливаем модель по умолчанию для Mistral API, если не указана
            if self.model_name == "mistral":
                self.model_name = "mistral-small-latest"

            self._check_mistral_api_availability()
        else:
            raise ValueError(
                f"Неизвестный провайдер: {self.provider}. Используйте 'ollama' или 'mistral_api'"
            )

        # Состояние диалога
        self.current_command = None  # Текущая команда в многошаговом диалоге
        self.pending_params = []  # Список ожидаемых параметров
        self.collected_params = {}  # Собранные параметры
        self.param_index = 0  # Индекс ожидаемого параметра

    def _check_ollama_availability(self):
        """Проверяет доступность Ollama и выбранной модели."""
        try:
            response = requests.get(f"{self.ollama_url}/api/tags")
            if response.status_code != 200:
                raise ConnectionError(
                    f"Не удалось подключиться к Ollama API: {response.status_code}"
                )

            # Проверяем, доступна ли указанная модель
            models = response.json().get("models", [])
            model_names = [model.get("name").split(":")[0] for model in models]

            if self.model_name not in model_names:
                print(f"Предупреждение: Модель {self.model_name} не найдена в Ollama.")
                print(f"Доступны следующие модели: {', '.join(model_names)}")
                print(
                    f"Пожалуйста, установите модель командой: ollama pull {self.model_name}"
                )
        except Exception as e:
            print(f"Ошибка при проверке доступности Ollama: {e}")
            print(
                "Убедитесь, что Ollama запущена (ollama serve) и доступна по адресу:",
                self.ollama_url,
            )
            print("Инструкции по установке: https://github.com/ollama/ollama")

    def _check_mistral_api_availability(self):
        """Проверяет доступность API Mistral и валидность API ключа."""
        try:
            headers = {
                "Authorization": f"Bearer {self.mistral_api_key}",
                "Content-Type": "application/json",
            }

            response = requests.get("https://api.mistral.ai/v1/models", headers=headers)

            if response.status_code == 401:
                raise ValueError(
                    "Недействительный ключ API Mistral. Проверьте свой ключ."
                )
            elif response.status_code != 200:
                raise ConnectionError(
                    f"Ошибка при подключении к Mistral API: {response.status_code}"
                )

            # Проверяем, доступна ли выбранная модель
            models = response.json().get("data", [])
            available_models = [model.get("id") for model in models]

            current_models = [
                "mistral-small-latest",  # указывает на текущую версию mistral-small норм
                "mistral-large-latest",  # указывает на текущую версию mistral-large долго
                "mistral-small-2503",  # Mistral Small 3.1 долго
                "mistral-small-2402",  # предыдущая версия Mistral Small долго
                "mistral-large-2411",  # последняя версия Mistral Large
                "mistral-medium-2312",  # Mistral Medium (устаревшая) не тестировал
                "open-mistral-7b",  # открытая модель 7B (устаревшая) не тестировал
                "open-mixtral-8x7b",  # открытая модель 8x7B (устаревшая) не тестировал
                "ministral-8b-latest",  # Ministral 8B (маленькая модель) норм
                "ministral-3b-latest",  # Ministral 3B (очень маленькая модель) очень хорошо
                "open-mistral-nemo",  # многоязычная модель не тестировал
            ]

            if self.model_name not in available_models:
                # Проверяем, соответствует ли модель текущим моделям
                if self.model_name in current_models:
                    print(
                        f"Предупреждение: Модель {self.model_name} не найдена в списке доступных моделей API, возможно у вас нет доступа к ней."
                    )
                    print("Попробуем использовать mistral-small-latest по умолчанию.")
                    self.model_name = "mistral-small-latest"
                else:
                    available_models_str = ", ".join(available_models)
                    print(
                        f"Предупреждение: Модель {self.model_name} не найдена в Mistral API."
                    )
                    print(f"Доступные модели: {available_models_str}")

                    # Устанавливаем модель по умолчанию
                    self.model_name = "mistral-small-latest"
                    print(f"Используем модель по умолчанию: {self.model_name}")

                # Проверяем, доступна ли модель по умолчанию
                if self.model_name not in available_models:
                    print(
                        f"Предупреждение: Модель по умолчанию {self.model_name} также недоступна."
                    )
                    # Выбираем первую доступную модель из списка
                    if available_models:
                        self.model_name = available_models[0]
                        print(f"Используем первую доступную модель: {self.model_name}")
                    else:
                        raise ValueError(
                            "Нет доступных моделей в вашей учетной записи Mistral API."
                        )
        except Exception as e:
            print(f"Ошибка при проверке API Mistral: {e}")

    def get_locale(self) -> Dict[str, Any]:
        """Возвращает текущую локализацию"""
        return LOCALES[self.current_language]

    def build_prompt(self, query: str) -> str:
        """Создает промпт для определения команды"""
        locale = self.get_locale()
        examples = "\n".join([f"- {v} -> {k}" for k, v in locale["commands"].items()])

        if self.provider == "ollama":
            prompt = locale["prompts"]["system"] + "\n\n" + locale["prompts"]["user"].format(
                examples=examples,
                query=query
            )
            return prompt
        else:  # mistral_api
            return {
                "system": locale["prompts"]["system"],
                "user": locale["prompts"]["user"].format(
                    examples=examples,
                    query=query
                )
            }

    def get_answer_to_question(self, question: str) -> str:
        """
        Получает ответ на вопрос пользователя с использованием LLM.

        Args:
            question: Вопрос пользователя

        Returns:
            Ответ от LLM
        """
        try:
            # Определяем язык вопроса
            lang = detect_language(question)
            locale = LOCALES[lang]

            # Проверяем на эмоциональные паттерны
            question_lower = question.lower()
            for emotion_type, patterns in locale["responses"]["emotional_patterns"].items():
                if any(pattern in question_lower for pattern in patterns):
                    return locale["responses"]["emotional_responses"][emotion_type]

            # Проверяем, является ли вопрос о помощнике
            assistant_questions = {
                "ru": ["кто ты", "что ты", "как тебя зовут", "как тебя звать"],
                "en": ["who are you", "what are you", "what's your name", "who are u"],
                "es": ["quién eres", "qué eres", "cómo te llamas"]
            }

            question_lower = question.lower()
            for lang_questions in assistant_questions.values():
                if any(q in question_lower for q in lang_questions):
                    return locale["responses"]["assistant_description"]

            if self.provider == "ollama":
                # Формируем промпт для Ollama на языке вопроса
                prompt = locale["prompts"]["question"].format(question=question)

                # Вызываем Ollama API
                response = requests.post(
                    f"{self.ollama_url}/api/generate",
                    json={
                        "model": self.model_name,
                        "prompt": prompt,
                        "stream": False,
                        "temperature": 0.7,
                        "max_tokens": 500,
                    },
                )

                if response.status_code != 200:
                    raise Exception(f"Ошибка API Ollama: {response.status_code}")

                result = response.json()
                answer = result.get("response", "").strip()
                return answer
            else:  # mistral_api
                # Формируем промпт для Mistral API на языке вопроса
                system_prompt = locale["prompts"]["system"]
                user_prompt = locale["prompts"]["question"].format(question=question)

                # Вызываем Mistral API с повторными попытками при ошибке
                headers = {
                    "Authorization": f"Bearer {self.mistral_api_key}",
                    "Content-Type": "application/json",
                }

                data = {
                    "model": self.model_name,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    "temperature": 0.7,
                    "max_tokens": 500,
                }

                max_retries = 3
                retry_count = 0
                retry_delay = 2  # начальная задержка в секундах

                while retry_count < max_retries:
                    response = requests.post(
                        "https://api.mistral.ai/v1/chat/completions",
                        headers=headers,
                        json=data,
                    )

                    if response.status_code == 200:
                        break
                    elif response.status_code == 429:
                        # Превышен лимит запросов - делаем паузу и повторяем
                        retry_count += 1
                        if retry_count < max_retries:
                            print(
                                f"Превышен лимит запросов к API Mistral. Повторная попытка через {retry_delay} сек..."
                            )
                            import time

                            time.sleep(retry_delay)
                            retry_delay *= 2  # увеличиваем задержку экспоненциально
                        else:
                            raise Exception(
                                f"Ошибка API Mistral при получении ответа: превышен лимит запросов (429)"
                            )
                    else:
                        # Другая ошибка
                        raise Exception(
                            f"Ошибка API Mistral при получении ответа: {response.status_code}"
                        )

                result = response.json()
                answer = result["choices"][0]["message"]["content"].strip()
                return answer

        except Exception as e:
            print(f"Ошибка при получении ответа на вопрос: {e}")
            return locale["responses"]["error"]

    def classify_command(self, query: str) -> str:
        """
        Определяет команду на основе запроса пользователя.

        Args:
            query: Запрос пользователя

        Returns:
            Ключ команды из словаря COMMANDS
        """
        prompt = self.build_prompt(query)

        try:
            if self.provider == "ollama":
                # Используем Ollama API
                response = requests.post(
                    f"{self.ollama_url}/api/generate",
                    json={
                        "model": self.model_name,
                        "prompt": prompt,
                        "stream": False,
                        "temperature": 0.1,
                        "top_p": 0.9,
                    },
                )

                if response.status_code != 200:
                    raise Exception(f"Ошибка API Ollama: {response.status_code}")

                result = response.json()
                command = result.get("response", "").strip()
            else:  # mistral_api
                # Используем Mistral API с повторными попытками при ошибке превышения лимита запросов
                headers = {
                    "Authorization": f"Bearer {self.mistral_api_key}",
                    "Content-Type": "application/json",
                }

                data = {
                    "model": self.model_name,
                    "messages": [
                        {"role": "system", "content": prompt["system"]},
                        {"role": "user", "content": prompt["user"]},
                    ],
                    "temperature": 0.1,
                    "top_p": 0.9,
                    "max_tokens": 10,  # Нам нужен только короткий ответ
                }

                max_retries = 3
                retry_count = 0
                retry_delay = 2  # начальная задержка в секундах

                while retry_count < max_retries:
                    response = requests.post(
                        "https://api.mistral.ai/v1/chat/completions",
                        headers=headers,
                        json=data,
                    )

                    if response.status_code == 200:
                        break
                    elif response.status_code == 429:
                        # Превышен лимит запросов - делаем паузу и повторяем
                        retry_count += 1
                        if retry_count < max_retries:
                            print(
                                f"Превышен лимит запросов к API Mistral. Повторная попытка через {retry_delay} сек..."
                            )
                            import time

                            time.sleep(retry_delay)
                            retry_delay *= 2  # увеличиваем задержку экспоненциально
                        else:
                            raise Exception(
                                f"Ошибка API Mistral: превышен лимит запросов (429)"
                            )
                    else:
                        # Другая ошибка
                        raise Exception(f"Ошибка API Mistral: {response.status_code}")

                result = response.json()
                command = result["choices"][0]["message"]["content"].strip()

            # Очищаем от возможных лишних символов и кавычек
            command = command.strip("\"`'").strip()

            if command not in COMMANDS and command not in ["unknown", "не знаю"]:
                # По умолчанию считаем это вопросом, если не распознали команду
                command = "answer_question"

            return command
        except Exception as e:
            print(f"Ошибка при классификации команды: {e}")
            return "unknown"

    def convert_time_to_numeric(self, time_str: str) -> str:
        """
        Преобразует время из словесного формата в числовой формат.

        Args:
            time_str: Время в словесном формате (например, "7 утра", "три часа дня")

        Returns:
            Время в формате "HH:MM"
        """
        # Ищем часы и минуты
        time_match = re.search(
            r"(?:на )?(\d{1,2}|[а-я]+)(?::(\d{2}))?\s*(утра|дня|вечера|ночи)?",
            time_str.lower(),
        )
        if not time_match:
            return time_str

        hours_str = time_match.group(1)
        minutes = time_match.group(2) or "00"
        period = time_match.group(3)

        # Преобразуем текстовое число в цифровое
        if hours_str.isalpha():
            hours = NUMBER_WORDS.get(hours_str, 0)
        else:
            hours = int(hours_str)

        if period == "дня" or period == "вечера":
            if hours < 12:
                hours += 12
        elif period == "ночи":
            if hours < 12:
                hours += 12

        # Форматируем часы и минуты с ведущими нулями
        hours = f"{hours:02d}"
        minutes = f"{int(minutes):02d}"

        return f"{hours}:{minutes}"

    def _parse_time(self, query: str) -> Dict[str, str]:
        """Парсит время из запроса"""
        locale = self.get_locale()
        params = {}
        number_words = locale["number_words"]
        time_periods = locale["time_periods"]

        # Получаем текущее время
        current_time = time.localtime()
        current_hour = current_time.tm_hour
        current_min = current_time.tm_min

        def convert_to_24h(hours, period=None):
            if period:
                if period in ["дня", "вечера", "afternoon", "evening", "tarde", "noche"]:
                    if hours < 12:
                        hours += 12
            return hours

        def get_target_date(target_hour, target_min, date_str=None):
            if date_str:
                if re.match(r"\d{1,2}\.\d{1,2}", date_str):
                    return date_str
                elif any(word in date_str.lower() for word in ["завтра", "tomorrow", "mañana"]):
                    tomorrow = time.localtime(time.time() + 86400)
                    return f"{tomorrow.tm_mday:02d}.{tomorrow.tm_mon:02d}"
                elif any(word in date_str.lower() for word in ["сегодня", "today", "hoy"]):
                    return f"{current_time.tm_mday:02d}.{current_time.tm_mon:02d}"

            if (target_hour < current_hour) or (target_hour == current_hour and target_min <= current_min):
                tomorrow = time.localtime(time.time() + 86400)
                return f"{tomorrow.tm_mday:02d}.{tomorrow.tm_mon:02d}"
            else:
                return f"{current_time.tm_mday:02d}.{current_time.tm_mon:02d}"

        # Пытаемся распознать время в различных форматах
        time_patterns = [
            # Формат HH:MM с датой
            (r"(\d{1,2}):(\d{2})\s+(.*)", lambda m: (int(m.group(1)), int(m.group(2)), None, m.group(3))),
            # Формат HH MM с датой
            (r"(\d{1,2})\s+(\d{2})\s+(.*)", lambda m: (int(m.group(1)), int(m.group(2)), None, m.group(3))),
            # Формат "число период" с датой (например, "семь утра завтра")
            (r"([а-я]+)\s+(утра|дня|вечера|ночи)\s+(.*)", lambda m: (number_words.get(m.group(1), 0), 0, m.group(2), m.group(3))),
            # Формат "число период" с датой (например, "7 утра завтра")
            (r"(\d{1,2})\s+(утра|дня|вечера|ночи)\s+(.*)", lambda m: (int(m.group(1)), 0, m.group(2), m.group(3))),
            # Формат HH:MM
            (r"(\d{1,2}):(\d{2})", lambda m: (int(m.group(1)), int(m.group(2)), None, None)),
            # Формат HH MM
            (r"(\d{1,2})\s+(\d{2})", lambda m: (int(m.group(1)), int(m.group(2)), None, None)),
            # Формат "число период" (например, "семь утра")
            (r"([а-я]+)\s+(утра|дня|вечера|ночи)", lambda m: (number_words.get(m.group(1), 0), 0, m.group(2), None)),
            # Формат "число период" (например, "7 утра")
            (r"(\d{1,2})\s+(утра|дня|вечера|ночи)", lambda m: (int(m.group(1)), 0, m.group(2), None)),
            # Испанский формат "число a.m./p.m."
            (r"(\d{1,2})\s*(?:a\.m\.|am|p\.m\.|pm)", lambda m: (int(m.group(1)), 0, "утра" if "a" in m.group(0).lower() else "дня", None)),
            # Испанский формат "las число"
            (r"las?\s+(\d{1,2})", lambda m: (int(m.group(1)), 0, None, None)),
            # Испанский формат "число de la mañana/tarde/noche"
            (r"(\d{1,2})\s+de\s+la\s+(mañana|tarde|noche)", lambda m: (int(m.group(1)), 0, m.group(2), None)),
            # Испанский формат "a las число"
            (r"a\s+las?\s+(\d{1,2})", lambda m: (int(m.group(1)), 0, None, None))
        ]

        for pattern, converter in time_patterns:
            match = re.search(pattern, query.lower())
            if match:
                try:
                    hours, minutes, period, date_str = converter(match)
                    if 0 <= hours <= 23 and 0 <= minutes <= 59:
                        # Преобразуем в 24-часовой формат
                        hours = convert_to_24h(hours, period)
                        time_str = f"{hours:02d}:{minutes:02d}"
                        params["time"] = time_str
                        params["date"] = get_target_date(hours, minutes, date_str)
                        break
                except (ValueError, TypeError):
                    continue

        return params

    def extract_parameters(self, command: str, query: str) -> Dict[str, str]:
        """
        Извлекает параметры из запроса пользователя.

        Args:
            command: Команда
            query: Запрос пользователя

        Returns:
            Словарь с извлеченными параметрами
        """
        params = {}

        if command == "call_contact":
            # Ищем имя контакта
            match = re.search(r"позвон[а-я]+ (\w+)", query.lower())
            if match:
                params["contact"] = match.group(1)

        elif command == "send_message":
            # Ищем имя контакта
            match = re.search(r"сообщение (\w+)", query.lower())
            if match:
                params["contact"] = match.group(1)

            # Если есть текст после "текст" или "текст сообщения"
            message_match = re.search(
                r"(?:текст|сообщение)[^а-яА-Я]*(.*?)$", query.lower()
            )
            if message_match and message_match.group(1).strip():
                params["message"] = message_match.group(1).strip()

        elif command == "set_alarm":
            # Используем новую функцию для парсинга времени
            time_params = self._parse_time(query)
            params.update(time_params)

        elif command == "search_internet" or command == "answer_question":
            # Для коротких вопросов (1-3 слова) - используем весь текст запроса
            words = query.split()
            if len(words) <= 3:
                params["query"] = query.strip()
                return params

            # Проверяем различные шаблоны вопросов
            question_patterns = [
                r"(?:найди|поищи|что такое|кто такой|как|расскажи о|объясни|какая)[^а-яА-Я]*(.*?)$",
                r"^(что [^?]+)\??$",
                r"^(кто [^?]+)\??$",
                r"^(как [^?]+)\??$",
                r"^(почему [^?]+)\??$",
                r"^(зачем [^?]+)\??$",
                r"^(где [^?]+)\??$",
                r"^(когда [^?]+)\??$",
                r"^(какая [^?]+)\??$",
            ]

            # Проверяем все шаблоны
            query_text = None
            for pattern in question_patterns:
                match = re.search(pattern, query.lower())
                if match and match.group(1).strip():
                    query_text = match.group(1).strip()
                    break

            # Если нет совпадений по шаблонам, используем весь текст запроса
            if not query_text:
                query_text = query.strip()

            # Очищаем запрос от лишних слов
            query_text = re.sub(r"^(какая|какой|какое|какие)\s+", "", query_text)
            query_text = re.sub(r"\s+(будет|есть|было|были)\s+", " ", query_text)

            params["query"] = query_text

        elif command == "open_browser":
            # Ищем поисковый запрос после слов "открой браузер" или "найди"
            browser_patterns = [
                r"открой браузер (?:и )?(?:найди|посмотри|покажи|ищи|найти|посмотреть|показать|искать)[^а-яА-Я]*(.*?)$",
                r"открой браузер (?:и )?(.*?)$",
                r"найди в браузере (.*?)$",
            ]

            query_text = None
            for pattern in browser_patterns:
                match = re.search(pattern, query.lower())
                if match and match.group(1).strip():
                    query_text = match.group(1).strip()
                    break

            if query_text:
                params["query"] = query_text

        elif command == "create_note":
            # Ищем название и содержимое заметки
            note_patterns = [
                r"создай заметку (?:под названием |названием )?([^,]+)(?:,| и)?(?: содержимое | текст | записать )?(.*?)$",
                r"создай заметку (?:с названием |названием )?([^,]+)(?:,| и)?(?: содержимое | текст | записать )?(.*?)$",
                r"создай заметку (?:с названием |названием )?([^,]+)$",
            ]

            for pattern in note_patterns:
                match = re.search(pattern, query.lower())
                if match:
                    if match.group(1).strip():
                        params["title"] = match.group(1).strip()
                    if len(match.groups()) > 1 and match.group(2).strip():
                        params["content"] = match.group(2).strip()
                    break

        elif command == "search_note":
            # Ищем поисковый запрос для заметок
            search_patterns = [
                r"найди в заметках (.*?)$",
                r"найди заметку (.*?)$",
                r"поищи в заметках (.*?)$",
            ]

            for pattern in search_patterns:
                match = re.search(pattern, query.lower())
                if match and match.group(1).strip():
                    params["query"] = match.group(1).strip()
                    break

        elif command == "open_note":
            # Ищем название заметки для открытия
            open_patterns = [
                r"открой заметку (?:под названием |с названием |названием )?(.*?)$",
                r"открой заметку (.*?)$",
            ]

            for pattern in open_patterns:
                match = re.search(pattern, query.lower())
                if match and match.group(1).strip():
                    params["title"] = match.group(1).strip()
                    break

        return params

    def process_input(self, user_input: str) -> Dict[str, Any]:
        """Обрабатывает пользовательский запрос"""
        # Определяем язык запроса
        self.current_language = detect_language(user_input)
        locale = self.get_locale()

        try:
            # Если у нас есть текущий диалог и ожидаются параметры
            if self.current_command and self.pending_params:
                param_name = self.pending_params[self.param_index]
                if self.current_command == "set_alarm" and param_name == "time":
                    time_params = self._parse_time(user_input)
                    self.collected_params.update(time_params)
                else:
                    self.collected_params[param_name] = user_input

                self.param_index += 1

                if self.param_index >= len(self.pending_params):
                    result = self._build_result()
                    self._reset_dialog_state()
                    return result
                else:
                    next_param = self.pending_params[self.param_index]
                    prompt = locale["command_transitions"][self.current_command]["prompts"][self.param_index]
                    return {
                        "command": self.current_command,
                        "response": prompt,
                        "params": self.collected_params,
                    }

            # Обычный запрос
            command = self.classify_command(user_input)
            extracted_params = self.extract_parameters(command, user_input)

            if command in locale["parameterized_commands"]:
                required_params = locale["parameterized_commands"][command]
                missing_params = [p for p in required_params if p not in extracted_params]

                if missing_params and command in locale["command_transitions"]:
                    self.current_command = command
                    self.pending_params = locale["command_transitions"][command]["expected_params"]
                    self.collected_params = extracted_params

                    for i, param in enumerate(self.pending_params):
                        if param not in extracted_params:
                            self.param_index = i
                            break

                    prompt = locale["command_transitions"][command]["prompts"][self.param_index]
                    return {
                        "command": command,
                        "response": prompt,
                        "params": extracted_params,
                    }

            response = locale["responses"].get(command, locale["responses"]["unknown"])

            if command == "answer_question" and "query" in extracted_params:
                query = extracted_params["query"]
                answer = self.get_answer_to_question(query)
                response = f"{locale['responses']['answer_question']} {answer}"

            return {
                "command": command,
                "response": response,
                "params": extracted_params,
            }
        except Exception as e:
            print(f"Ошибка при обработке запроса: {e}")
            return {
                "command": "error",
                "response": locale["responses"]["error"],
                "params": {"error_details": str(e)},
            }

    def _build_result(self) -> Dict[str, Any]:
        """
        Формирует результат команды после сбора всех параметров.

        Returns:
            Словарь с результатом
        """
        command = self.current_command
        locale = self.get_locale()
        response = locale["responses"].get(command, locale["responses"]["unknown"])

        # Добавляем детали в зависимости от команды
        if command == "call_contact" and "contact" in self.collected_params:
            response = locale["responses"]["call_contact_success"].format(contact=self.collected_params["contact"])

        elif command == "send_message":
            if "contact" in self.collected_params:
                response = locale["responses"]["send_message_success"].format(
                    contact=self.collected_params["contact"],
                    message=self.collected_params.get("message", "")
                )

        elif command == "set_alarm":
            if "time" in self.collected_params:
                time_str = self.collected_params["time"]
                if "date" in self.collected_params:
                    date_str = self.collected_params["date"]
                    response = locale["responses"]["set_alarm_success"].format(time=f"{date_str} {time_str}")
                else:
                    response = locale["responses"]["set_alarm_success"].format(time=time_str)

        elif command == "create_event":
            if "title" in self.collected_params:
                response = locale["responses"]["create_event_success"].format(
                    title=self.collected_params["title"],
                    time=self.collected_params.get("time", "")
                )

        elif command == "create_note":
            if "title" in self.collected_params:
                response = locale["responses"]["create_note_success"].format(
                    title=self.collected_params["title"],
                    content=self.collected_params.get("content", "")
                )

        elif command == "search_note":
            if "query" in self.collected_params:
                response = locale["responses"]["search_note_success"].format(
                    query=self.collected_params["query"]
                )

        elif command == "open_note":
            if "title" in self.collected_params:
                response = locale["responses"]["open_note_success"].format(
                    title=self.collected_params["title"]
                )

        elif command == "open_browser":
            if "query" in self.collected_params:
                response = locale["responses"]["open_browser_success"].format(
                    query=self.collected_params["query"]
                )

        return {
            "command": command,
            "response": response,
            "params": self.collected_params,
        }

    def _reset_dialog_state(self) -> None:
        """Сбрасывает состояние диалога."""
        self.current_command = None
        self.pending_params = []
        self.collected_params = {}
        self.param_index = 0

    def process_input_json(self, user_input: str) -> str:
        """
        Обрабатывает запрос и возвращает результат в формате JSON.

        Args:
            user_input: Текст запроса пользователя

        Returns:
            JSON-строка с результатом
        """
        result = self.process_input(user_input)
        return json.dumps(result, ensure_ascii=False)


# # код для тестирования
# if __name__ == "__main__":
#     # Определяем, какой режим использовать
#     mistral_api_key = os.environ.get("MISTRAL_API_KEY")

#     use_mistral_api = True  # По умолчанию используем Mistral API

#     if not mistral_api_key:
#         # Если ключ не найден в переменных окружения, предлагаем его ввести
#         print("API ключ Mistral не найден в переменных окружения.")
#         use_mistral_option = safe_input("Хотите использовать Mistral API? (да/нет): ", encoding).lower()

#         if use_mistral_option in ["да", "yes", "y", "д"]:
#             mistral_api_key = safe_input("Введите ваш ключ API Mistral: ", encoding).strip()
#             # Сохраняем ключ в переменных окружения для текущей сессии
#             os.environ["MISTRAL_API_KEY"] = mistral_api_key
#         else:
#             use_mistral_api = False

#     if use_mistral_api and mistral_api_key:
#         # Используем Mistral API
#         print("Используем Mistral API.")

#         # Список современных моделей для выбора
#         available_model_options = [
#             ("1", "mistral-small-latest", "Mistral Small - последняя версия (рекомендуется)"),
#             ("2", "mistral-large-latest", "Mistral Large - высокая производительность, дороже"),
#             ("3", "ministral-8b-latest", "Ministral 8B - малая модель, быстрее и дешевле"),
#             ("4", "ministral-3b-latest", "Ministral 3B - очень малая модель, самая быстрая"),
#             ("5", "open-mistral-nemo", "Open Mistral Nemo - открытая многоязычная модель")
#         ]

#         # Показываем список доступных моделей
#         print("\nДоступные модели Mistral API:")
#         for option, model_id, description in available_model_options:
#             print(f"{option}. {description} ({model_id})")

#         # Запрашиваем выбор модели
#         model_choice = safe_input("\nВыберите номер модели (или нажмите Enter для использования mistral-small-latest): ", encoding).strip()

#         # Определяем выбранную модель
#         selected_model = "mistral-small-latest"  # модель по умолчанию
#         for option, model_id, _ in available_model_options:
#             if model_choice == option:
#                 selected_model = model_id
#                 break

#         print(f"Выбрана модель: {selected_model}")

#         # Создаем ассистента с выбранной моделью
#         assistant = VoiceAssistant(
#             provider="mistral_api",
#             mistral_api_key=mistral_api_key,
#             model_name=selected_model
#         )
#     else:
#         # Иначе используем локальную модель через Ollama
#         print("Использование Mistral API отключено или ключ не предоставлен. Используем локальную модель через Ollama.")
#         assistant = VoiceAssistant(provider="ollama")
#         print("ВАЖНО: Убедитесь, что у вас установлена и запущена Ollama (https://ollama.com)")
#         print("Для загрузки модели выполните в терминале: ollama pull mistral")

#     print("\n=== Режим обработки внешних запросов ===")
#     print("Вводите текст запроса и нажимайте Enter. Этот текст будет обработан как внешний запрос.")
#     print("Для выхода введите 'выход', 'exit' или 'quit'")
#     print("=" * 40)

#     while True:
#         try:
#             # Получаем запрос
#             query = safe_input("", encoding).strip()

#             if not query:  # Пропускаем пустые строки
#                 continue

#             if query.lower() in ["выход", "exit", "quit"]:
#                 break

#             # Обрабатываем запрос
#             start_time = time.time()
#             result = assistant.process_input(query)
#             end_time = time.time()

#             # Выводим результат
#             try:
#                 print(json.dumps(result, ensure_ascii=False))
#             except Exception as e:
#                 print(f"Ошибка при выводе результата: {e}")
#                 error_result = {
#                     "command": "error",
#                     "response": RESPONSES["error"],
#                     "params": {"error_details": str(e)}
#                 }
#                 print(json.dumps(error_result, ensure_ascii=False))

#             # Выводим время обработки
#             print(f"Время обработки: {(end_time - start_time):.2f} сек")
#         except Exception as e:
#             print(f"Ошибка при обработке запроса: {e}")
#             error_result = {
#                 "command": "error",
#                 "response": RESPONSES["error"],
#                 "params": {"error_details": str(e)}
#             }
#             print(json.dumps(error_result, ensure_ascii=False))