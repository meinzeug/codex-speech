import logging
import sys
from pathlib import Path
import traceback


def configure_logging(config):
    log_level = _get_log_level(config)
    log_file = _get_log_file(config)

    handlers = [logging.StreamHandler(sys.stdout)]
    if log_file:
        log_path = Path(log_file).expanduser()
        log_path.parent.mkdir(parents=True, exist_ok=True)
        handlers.append(logging.FileHandler(log_path, encoding='utf-8'))

    logging.basicConfig(
        level=log_level,
        format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
        handlers=handlers,
    )


def install_excepthook(error_callback=None):
    def _handle(exc_type, exc_value, exc_traceback):
        if issubclass(exc_type, KeyboardInterrupt):
            sys.__excepthook__(exc_type, exc_value, exc_traceback)
            return
        logging.critical('Unhandled exception', exc_info=(exc_type, exc_value, exc_traceback))
        if error_callback:
            details = ''.join(traceback.format_exception(exc_type, exc_value, exc_traceback))
            error_callback(
                title='Application Crash',
                message='The application encountered an unexpected error.',
                details=details,
            )
    sys.excepthook = _handle


def _get_log_level(config):
    level = 'INFO'
    if config:
        level = config.get('advanced', {}).get('log_level', level)
    return getattr(logging, str(level).upper(), logging.INFO)


def _get_log_file(config):
    if not config:
        return None
    return config.get('advanced', {}).get('log_file')
